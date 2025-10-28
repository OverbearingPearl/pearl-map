(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.utils.geometry :as geom]
            ["maplibre-gl" :as maplibre]
            ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]
            ["three" :as three]))

(defn isExpression [x]
  (if (array? x)
    (and (string? (first x)) (> (count x) 1))
    (and (object? x)
         (or (some? (.-stops x))
             (some? (.-property x))
             (some? (.-type x))))))

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
  (let [map-element (.getElementById js/document "map-container")]
    (when map-element
      (let [map-config (create-config "raster-style")
            map-obj (maplibre/Map. map-config)]
        (set-map-instance! map-obj)
        (.addControl map-obj (maplibre/NavigationControl.))
        (.addControl map-obj (maplibre/ScaleControl.))
        map-obj))))

(defn on-map-load [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "load" #(callback map-obj))))

(defn on-map-error [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "error" #(callback %))))

(defn add-extruded-buildings-layer []
  (when-let [^js map-obj (get-map-instance)]
    (try
      (when (.getSource map-obj "carto")
        (when-not (.getLayer map-obj "extruded-building")
          (let [current-style (:current-style @db/app-db)
                initial-color (if (= current-style (:dark style-urls))
                                "#2d3748"   ;; Corresponds to default-building-styles :dark :fill-extrusion-color
                                "#f0f0f0")] ;; Corresponds to default-building-styles :light :fill-extrusion-color
            (let [layer-spec (clj->js
                              {:id "extruded-building"
                               :type "fill-extrusion"
                               :source "carto"
                               :source-layer "building"
                               :filter (into ["!in" "$id"] eiffel-tower-osm-ids)
                               :paint {:fill-extrusion-color
                                       (if (string? initial-color)
                                         initial-color
                                         "#f0f0f0")  ;; Fallback default color
                                       :fill-extrusion-height ["coalesce" ["get" "height"] ["get" "render_height"] 10]  ;; Minimum height of 10 meters
                                       :fill-extrusion-base ["coalesce" ["get" "min_height"] ["get" "render_min_height"] 0]
                                       :fill-extrusion-opacity 1.0
                                       :fill-extrusion-vertical-gradient true
                                       :fill-extrusion-translate [0, 0]
                                       :fill-extrusion-translate-anchor "map"}})
                  layer-type (.-type layer-spec)]
              (js/console.log "Adding extruded buildings layer with spec:" layer-spec "type:" layer-type)
              (.addLayer map-obj layer-spec))
            (.on map-obj "click" "extruded-building"
                 (fn [e]
                   (when-let [feature (first (.-features e))]
                     (js/console.log "Clicked Feature --- ID:" (.-id feature) "--- Properties:" (js->clj (.-properties feature) :keywordize-keys true))))))))
      (catch js/Error e
        (js/console.error "Failed to add extruded buildings layer:" e)))))

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
  (let [^js map-obj (get-map-instance)]
    (.setCenter map-obj (:center state))
    (.setZoom map-obj (:zoom state))
    (.setPitch map-obj (:pitch state))
    (.setBearing map-obj (:bearing state))))

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

(defn set-paint-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      ;; Skip if value is nil or undefined
      (when (some? value)
        ;; Convert Clojure maps to JavaScript objects for expressions
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
          (try
            (js/console.log "Setting paint property:" layer-id property-name js-value "on layer type:" layer-type)
            ;; For fill-extrusion layers, ensure the property is supported
            (when (or (not= "fill-extrusion-color" property-name)
                      (= "fill-extrusion" layer-type))
              (.setPaintProperty map-obj layer-id property-name js-value))
            (catch js/Error e
              (js/console.error (str "Failed to set property " property-name
                                     " on layer " layer-id " (type: " layer-type "):") e)
              (throw e))))))))

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
      (and (string? color-value) (= color-value "transparent"))
      "transparent"

      (isExpression color-value)
      (try
        (let [result (evaluate color-value #js {:zoom current-zoom})]
          (if (or (string? result) (= result "transparent"))
            result
            (do
              (js/console.warn "Expression evaluated to non-string color:" result)
              nil)))
        (catch js/Error e
          (js/console.warn "Failed to evaluate color expression:" e)
          nil))

      (string? color-value)
      (if (.startsWith color-value "#")
        color-value
        (try
          (-> (color color-value)
              (.hex)
              (.toString)
              (.toLowerCase))
          (catch js/Error e
            (js/console.warn "Failed to parse color string:" color-value)
            nil)))

      (number? color-value)
      (try
        (str "#" (.toString (js/Math.floor color-value) 16))
        (catch js/Error e
          (js/console.warn "Failed to convert numeric to color:" color-value)
          nil))

      (object? color-value)
      (try
        (let [value (or (.-default color-value)
                        (.-value color-value)
                        (.-valueOf color-value))]
          (parse-color-expression value current-zoom))
        (catch js/Error e
          nil))

      :else
      (do
        (js/console.warn "Unexpected color value type:" (type color-value))
        nil))))

(defn parse-numeric-expression [numeric-value current-zoom]
  (cond
    (nil? numeric-value) nil

    (number? numeric-value) numeric-value

    (isExpression numeric-value)
    (try
      (let [result (evaluate numeric-value #js {:zoom current-zoom})]
        (cond
          (number? result) result
          (string? result) (let [parsed (js/parseFloat result)]
                             (if (js/isNaN parsed) nil parsed))
          :else nil))
      (catch js/Error e
        (js/console.warn "Failed to evaluate numeric expression:" e)
        nil))

    (string? numeric-value) (let [parsed (js/parseFloat numeric-value)]
                              (if (js/isNaN parsed) nil parsed))

    (object? numeric-value)
    (let [value (or (.-default numeric-value)
                    (.-value numeric-value)
                    (.-valueOf numeric-value))]
      (cond
        (number? value) value
        (string? value) (let [parsed (js/parseFloat value)]
                          (if (js/isNaN parsed) nil parsed))
        :else nil))

    :else nil))

(defn get-zoom-value-pairs [layer-id property-name current-zoom]
  (let [value (get-paint-property layer-id property-name)]
    (when value
      (let [clj-value (js->clj value :keywordize-keys true)]
        (cond
          ;; object-style stops
          (and (map? clj-value) (:stops clj-value))
          (mapv (fn [[zoom prop-value]]
                  {:zoom zoom
                   :value (if (#{"fill-color" "fill-outline-color" "fill-extrusion-color"} property-name)
                            (parse-color-expression prop-value current-zoom)
                            (parse-numeric-expression prop-value current-zoom))})
                (:stops clj-value))

          ;; array-style interpolate on zoom
          (and (vector? clj-value)
               (>= (count clj-value) 4)
               (= "interpolate" (first clj-value))
               (= ["zoom"] (get clj-value 2)))
          (let [stops-pairs (partition 2 (subvec clj-value 3))]
            (mapv (fn [[zoom prop-value]]
                    {:zoom zoom
                     :value (if (#{"fill-color" "fill-outline-color" "fill-extrusion-color"} property-name)
                              (parse-color-expression prop-value current-zoom)
                              (parse-numeric-expression prop-value current-zoom))})
                  stops-pairs))

          :else
          [{:zoom current-zoom
            :value (if (#{"fill-color" "fill-outline-color" "fill-extrusion-color"} property-name)
                     (parse-color-expression value current-zoom)
                     (parse-numeric-expression value current-zoom))}])))))

(defn update-single-value [layer-id property-name new-value]
  "Update a property to a single value (not a stops expression)"
  new-value)

(defn update-zoom-value-pair [layer-id property-name zoom new-value]
  (let [current-value (get-paint-property layer-id property-name)
        current-zoom (get-current-zoom)
        clj-value (if (some? current-value) (js->clj current-value :keywordize-keys true) nil)]
    (cond
      ;; Not an expression, create one if needed
      (not (isExpression current-value))
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
  (try
    (and (map? style)
         (every? (fn [[k v]]
                   (and (keyword? k)
                        (or (string? v)
                            (number? v)
                            (nil? v)
                            (and (object? v)
                                 (or (some? (.-stops v))
                                     (some? (.-property v))
                                     (some? (.-type v))))
                            (and (map? v)
                                 (or (:stops v)
                                     (:property v)
                                     (:type v))))))
                 style))
    (catch js/Error e
      false)))
