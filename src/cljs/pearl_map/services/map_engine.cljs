(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.utils.geometry :as geom]
            ["maplibre-gl" :as maplibre]
            ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]
            ["three" :as three]))

(defn expression? [x]
  (cond
    (array? x) (and (string? (first x)) (> (count x) 1))
    (object? x) (or (some? (.-stops x))
                    (some? (.-property x))
                    (some? (.-type x)))
    :else false))

(defn- evaluate [expr properties]
  (cond
    (and (.-stops expr) (.-zoom properties))
    (let [stops-array (.-stops expr)
          stops (vec (js->clj stops-array))
          zoom (.-zoom properties)
          base (or (.-base expr) 1)
          stop-count (count stops)]

      (if (zero? stop-count)
        nil
        (loop [i 0]
          (cond
            (>= i stop-count)
            (second (nth stops (dec stop-count)))

            :else
            (let [[current-zoom current-value] (nth stops i)]
              (if (<= zoom current-zoom)
                (if (zero? i)
                  current-value
                  (let [[prev-zoom prev-value] (nth stops (dec i))
                        t (/ (- zoom prev-zoom) (- current-zoom prev-zoom))
                        interpolated-t (if (= base 1)
                                         t
                                         (/ (- (js/Math.pow base t) 1)
                                            (- base 1)))]
                    (cond
                      (and (number? prev-value) (number? current-value))
                      (+ prev-value (* (- current-value prev-value) interpolated-t))

                      :else
                      current-value)))
                (recur (inc i))))))))

    (.-value expr) (.-value expr)
    (.-default expr) (.-default expr)

    :else expr))

(def eiffel-tower-coords [2.2945 48.8584])

(def eiffel-tower-osm-ids
  "OSM IDs for buildings in the Eiffel Tower complex to be excluded from the map."
  [;; Main structure:
   5013364
   ;; Other structures that have little impact (IDs identified via click debugging):
   ;; 278644
   ;; 279659
   ;; 540568
   ;; 540590]
   ;; Surrounding structures:
   308687745
   308687744
   308689164
   4114842
   4114839
   308687746
   308145239
   69034127
   335101043
   4114841])

(def style-urls
  {:basic "raster-style"
   :dark "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
   :light "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"})

(defn get-map-instance []
  (:map-instance @db/app-db))

(defn get-custom-layers []
  (:custom-layers @db/app-db))

(defn set-map-instance! [instance]
  (re-frame/dispatch [:set-map-instance instance])
  (set! (.-pearlMapInstance js/window) instance))

(defn create-config [style-url]
  (let [base-config {:container "map-container"
                     :center (clj->js eiffel-tower-coords)
                     :zoom 15
                     :pitch 45
                     :bearing 0
                     :attributionControl true
                     :maxZoom 19
                     :minZoom 0}]
    (if (= style-url "raster-style")
      (clj->js (assoc base-config
                      :style {:version 8
                              :name "OSM Bright"
                              :sources {:osm {:type "raster"
                                              :tiles ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"]
                                              :tileSize 256
                                              :attribution "© OpenStreetMap contributors"}}
                              :layers [{:id "osm-tiles"
                                        :type "raster"
                                        :source "osm"
                                        :minzoom 0
                                        :maxzoom 19}]}))
      (clj->js (assoc base-config :style style-url)))))

(defn init-map []
  (when (.getElementById js/document "map-container")
    (let [map-config (create-config "raster-style")
          map-obj (maplibre/Map. map-config)]
      (set-map-instance! map-obj)
      (doto map-obj
        (.addControl (maplibre/NavigationControl.))
        (.addControl (maplibre/ScaleControl.))))))

(defn on-map-load [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "load" #(callback map-obj))))

(defn on-map-error [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "error" #(callback %))))

(defn set-paint-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (and (.getLayer map-obj layer-id) (some? value))
      (let [js-value (cond
                       (map? value) (clj->js value)
                       (#{"fill-extrusion-color" "fill-color" "fill-outline-color"} property-name)
                       (cond
                         (= value "transparent") "transparent"
                         (string? value) (if (or (.startsWith value "#")
                                                 (.startsWith value "rgb")
                                                 (.startsWith value "hsl"))
                                           value
                                           (str "#" value))
                         :else (str "#" (.toString (js/parseInt (str value)) 16)))
                       :else value)
            layer-type (.-type (.getLayer map-obj layer-id))]
        (when (or (not= "fill-extrusion-color" property-name)
                  (= "fill-extrusion" layer-type))
          (.setPaintProperty map-obj layer-id property-name js-value))))))


(defn- create-extruded-layer-spec [layer-id initial-color]
  (let [is-top-layer? (= layer-id "extruded-building-top")
        height-expr ["coalesce" ["get" "height"] ["get" "render_height"] 10]
        base-expr (if is-top-layer?
                    height-expr
                    ["coalesce" ["get" "min_height"] ["get" "render_min_height"] 0])]
    (clj->js
     {:id layer-id
      :type "fill-extrusion"
      :source "carto"
      :source-layer "building"
      :filter (into ["!in" "$id"] eiffel-tower-osm-ids)
      :paint {:fill-extrusion-color initial-color
              :fill-extrusion-height height-expr
              :fill-extrusion-base base-expr
              :fill-extrusion-opacity 1.0
              :fill-extrusion-vertical-gradient (not is-top-layer?)
              :fill-extrusion-translate [0, 0]
              :fill-extrusion-translate-anchor "map"}})))

(defn add-extruded-buildings-layer []
  (when-let [^js map-obj (get-map-instance)]
    (when (.getSource map-obj "carto")
      (let [current-style (:current-style @db/app-db)
            initial-color (if (= current-style (:dark style-urls))
                            "#2d3748"
                            "#f0f0f0")]
        (doseq [layer-id ["extruded-building" "extruded-building-top"]]
          (when-not (.getLayer map-obj layer-id)
            (let [spec (create-extruded-layer-spec layer-id initial-color)]
              (.addLayer map-obj spec))))))))

(defn register-custom-layer [layer-id layer-impl]
  (re-frame/dispatch [:register-custom-layer layer-id layer-impl]))

(defn unregister-custom-layer [layer-id]
  (re-frame/dispatch [:unregister-custom-layer layer-id]))

(defn remove-custom-layer [layer-id]
  (let [^js map-obj (get-map-instance)]
    (.removeLayer map-obj layer-id)
    (unregister-custom-layer layer-id)))

(defn- get-current-map-state []
  (let [^js map-obj (get-map-instance)]
    (when map-obj
      {:center (.getCenter map-obj)
       :zoom (.getZoom map-obj)
       :pitch (.getPitch map-obj)
       :bearing (.getBearing map-obj)
       :layers (get-custom-layers)})))

(defn- apply-map-state! [state]
  (when-let [^js map-obj (get-map-instance)]
    (doto map-obj
      (.setCenter (:center state))
      (.setZoom (:zoom state))
      (.setPitch (:pitch state))
      (.setBearing (:bearing state)))))

(defn- clear-custom-layers []
  (let [^js map-obj (get-map-instance)
        layers (get-custom-layers)]
    (doseq [[layer-id _] layers]
      (when (.getLayer map-obj layer-id)
        (.removeLayer map-obj layer-id)))
    (re-frame/dispatch [:clear-custom-layers])))

(defn add-custom-layer [layer-id layer-impl before-id]
  (let [^js map-obj (get-map-instance)]
    (when-not (.getLayer map-obj layer-id)
      (.addLayer map-obj layer-impl before-id)
      (register-custom-layer layer-id layer-impl))))

(defn- reapply-custom-layers! [layers]
  (clear-custom-layers)
  (doseq [[layer-id layer-impl] layers]
    (add-custom-layer layer-id layer-impl nil)))

(defn- switch-to-raster-style [current-state]
  (let [^js map-obj (get-map-instance)]
    (.remove map-obj)
    (set-map-instance! nil)
    (re-frame/dispatch [:clear-custom-layers])
    (reagent/next-tick
     (fn []
       (init-map)
       (reagent/next-tick
        (fn []
          (let [new-map-obj (get-map-instance)]
            (.once new-map-obj "load"
                   (fn []
                     (apply-map-state! current-state)
                     (reapply-custom-layers! (:layers current-state))
                     (re-frame/dispatch [:style-editor/reset-styles-immediately]))))))))))

(defn- switch-to-vector-style [current-state style-url]
  (let [^js map-obj (get-map-instance)]
    (clear-custom-layers)
    (.setStyle map-obj style-url)
    (.once map-obj "idle"
           (fn []
             (add-extruded-buildings-layer)
             (reapply-custom-layers! (:layers current-state))
             (apply-map-state! current-state)))))

(defn change-map-style [style-url]
  (let [^js map-obj (get-map-instance)
        current-state (get-current-map-state)]
    (if (= style-url "raster-style")
      (switch-to-raster-style current-state)
      (switch-to-vector-style current-state style-url))))

(defn get-paint-property [layer-id property-name]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (try
        (.getPaintProperty map-obj layer-id property-name)
        (catch js/Error e
          ;; This can happen if property is not applicable for layer type.
          ;; MapLibre throws a TypeError in this case.
          ;; We can safely ignore it and return nil.
          nil)))))

(defn layer-exists? [layer-id]
  (when-let [^js map-obj (get-map-instance)]
    (.getLayer map-obj layer-id)))

(defn get-current-zoom []
  (when-let [^js map-obj (get-map-instance)]
    (.getZoom map-obj)))

(defn set-center [coords]
  (when-let [^js map-obj (get-map-instance)]
    (.setCenter map-obj (clj->js coords))))

(defn set-zoom [zoom-level]
  (when-let [^js map-obj (get-map-instance)]
    (.setZoom map-obj zoom-level)))

(defn set-pitch [pitch-angle]
  (when-let [^js map-obj (get-map-instance)]
    (.setPitch map-obj pitch-angle)))

(defn set-bearing [bearing-angle]
  (when-let [^js map-obj (get-map-instance)]
    (.setBearing map-obj bearing-angle)))

(defn parse-color-expression [color-value current-zoom]
  (when color-value
    (cond
      (string? color-value)
      (cond
        (= color-value "transparent") "transparent"
        (.startsWith color-value "#") color-value
        :else (-> (color color-value)
                  (.hex)
                  (.toString)
                  (.toLowerCase)))

      (expression? color-value)
      (let [result (evaluate color-value #js {:zoom current-zoom})]
        (if (or (string? result) (= result "transparent"))
          result
          (throw (js/Error. (str "Expression evaluated to non-string color: " result)))))

      (number? color-value)
      (str "#" (.toString (js/Math.floor color-value) 16))

      (object? color-value)
      (let [value (or (.-default color-value)
                      (.-value color-value)
                      (.-valueOf color-value))]
        (parse-color-expression value current-zoom))

      :else
      (throw (js/Error. (str "Unexpected color value type: " (type color-value)))))))

(defn parse-numeric-expression [numeric-value current-zoom]
  (cond
    (nil? numeric-value) nil
    (number? numeric-value) numeric-value

    (expression? numeric-value)
    (let [result (evaluate numeric-value #js {:zoom current-zoom})]
      (cond
        (number? result) result
        (string? result) (let [parsed (js/parseFloat result)]
                           (when-not (js/isNaN parsed) parsed))
        :else (throw (js/Error. (str "Expression evaluated to non-numeric value: " result)))))

    (string? numeric-value)
    (let [parsed (js/parseFloat numeric-value)]
      (when-not (js/isNaN parsed) parsed))

    (object? numeric-value)
    (let [value (or (.-default numeric-value)
                    (.-value numeric-value)
                    (.-valueOf numeric-value))]
      (cond
        (number? value) value
        (string? value) (let [parsed (js/parseFloat value)]
                          (when-not (js/isNaN parsed) parsed))
        :else nil))

    :else nil))

(defn get-zoom-value-pairs [layer-id property-name current-zoom]
  (let [value (get-paint-property layer-id property-name)]
    (when value
      (let [clj-value (js->clj value :keywordize-keys true)
            parse-fn (if (#{"fill-color" "fill-outline-color" "fill-extrusion-color"} property-name)
                       parse-color-expression
                       parse-numeric-expression)]
        (cond
          ;; object-style stops
          (and (map? clj-value) (:stops clj-value))
          (mapv (fn [[zoom prop-value]]
                  {:zoom zoom
                   :value (parse-fn prop-value current-zoom)})
                (:stops clj-value))

          ;; array-style interpolate on zoom
          (and (vector? clj-value)
               (>= (count clj-value) 4)
               (= "interpolate" (first clj-value))
               (= ["zoom"] (get clj-value 2)))
          (let [stops-pairs (partition 2 (subvec clj-value 3))]
            (mapv (fn [[zoom prop-value]]
                    {:zoom zoom
                     :value (parse-fn prop-value current-zoom)})
                  stops-pairs))

          :else
          [{:zoom current-zoom
            :value (parse-fn value current-zoom)}])))))

(defn update-single-value [layer-id property-name new-value]
  "Update a property to a single value (not a stops expression)"
  new-value)

(defn update-zoom-value-pair [layer-id property-name zoom new-value]
  (let [current-value (get-paint-property layer-id property-name)
        current-zoom (get-current-zoom)
        clj-value (if (some? current-value) (js->clj current-value :keywordize-keys true) nil)]
    (cond
      ;; Not an expression, create one if needed
      (not (expression? current-value))
      (if (and (not= zoom current-zoom) (some? current-value))
        ["interpolate" ["linear"] ["zoom"] current-zoom current-value zoom new-value]
        new-value)

      ;; object-style stops
      (and (map? clj-value) (:stops clj-value))
      (let [stops (vec (:stops clj-value))
            stop-exists? (some #(= zoom (first %)) stops)
            updated-stops (if stop-exists?
                            (mapv (fn [[stop-zoom stop-value]]
                                    (if (= stop-zoom zoom)
                                      [stop-zoom new-value]
                                      [stop-zoom stop-value]))
                                  stops)
                            (sort-by first (conj stops [zoom new-value])))]
        (assoc clj-value :stops updated-stops))

      ;; array-style interpolate on zoom
      (and (vector? clj-value)
           (>= (count clj-value) 4)
           (= "interpolate" (first clj-value))
           (= ["zoom"] (get clj-value 2)))
      (let [header (subvec clj-value 0 3)
            stops (subvec clj-value 3)
            stops-pairs (partition 2 stops)
            stop-exists? (some #(= zoom (first %)) stops-pairs)
            updated-stops-pairs (if stop-exists?
                                  (mapv (fn [[stop-zoom stop-value]]
                                          (if (= stop-zoom zoom)
                                            [stop-zoom new-value]
                                            [stop-zoom stop-value]))
                                        stops-pairs)
                                  (sort-by first (conj stops-pairs [zoom new-value])))
            updated-stops (reduce into [] updated-stops-pairs)]
        (vec (concat header updated-stops)))

      ;; Default case: Fallback to setting the new value as a constant.
      :else
      new-value)))

(defn validate-style [style]
  (and (map? style)
       (every? (fn [[k v]]
                 (and (keyword? k)
                      (or (string? v)
                          (number? v)
                          (nil? v)
                          (expression? v)
                          (and (map? v)
                               (or (:stops v)
                                   (:property v)
                                   (:type v))))))
               style)))
