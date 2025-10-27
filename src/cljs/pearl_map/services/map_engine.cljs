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
  (and (object? x)
       (or (some? (.-stops x))
           (some? (.-property x))
           (some? (.-type x)))))

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
  [5013364   ;; Eiffel Tower main structure
   308687745 ;; Another building part, identified via click debugging
   308687744 ;; Another building part, identified via click debugging
   308689164 ;; Another building part, identified via click debugging
   4114842   ;; Another building part, identified via click debugging
   4114839   ;; Another building part, identified via click debugging
   308687746 ;; Another building part, identified via click debugging
   308145239 ;; Another building part, identified via click debugging
   69034127  ;; Another building part, identified via click debugging
   278644    ;; Another building part, identified via click debugging
   279659    ;; Another building part, identified via click debugging
   540568    ;; Another building part, identified via click debugging
   335101043 ;; Another building part, identified via click debugging
   540590    ;; Another building part, identified via click debugging
   4114841   ;; Another building part, identified via click debugging
   ])

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

(defn add-buildings-layer []
  (when-let [^js map-obj (get-map-instance)]
    (try
      (when (.getSource map-obj "carto")
        (when-not (.getLayer map-obj "buildings")
          (.addLayer map-obj
                     (clj->js
                      {:id "buildings"
                       :type "fill-extrusion"
                       :source "carto"
                       :source-layer "building"
                       :filter (into ["!in" "$id"] eiffel-tower-osm-ids)
                       :paint {:fill-extrusion-color "#f0f0f0"
                               :fill-extrusion-height ["coalesce" ["get" "height"] ["get" "render_height"] 0]
                               :fill-extrusion-base ["coalesce" ["get" "min_height"] ["get" "render_min_height"] 0]
                               :fill-extrusion-opacity 1.0}}))
          (.on map-obj "click" "buildings"
               (fn [e]
                 (when-let [feature (first (.-features e))]
                   (js/console.log "Clicked Feature --- ID:" (.-id feature) "--- Properties:" (js->clj (.-properties feature) :keywordize-keys true)))))))
      (catch js/Error e
        (js/console.error "Failed to add buildings layer:" e)))))

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
             (add-buildings-layer)
             (reapply-custom-layers! (:layers current-state))
             (apply-map-state! current-state)
             (re-frame/dispatch [:style-editor/reset-styles-immediately])))))

(defn change-map-style [style-url]
  (let [^js map-obj (get-map-instance)
        current-state (get-current-map-state)]
    (if (= style-url "raster-style")
      (switch-to-raster-style current-state)
      (switch-to-vector-style current-state style-url))))

(defn get-paint-property [layer-id property-name]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (.getPaintProperty map-obj layer-id property-name))))

(defn layer-exists? [layer-id]
  (when-let [^js map-obj (get-map-instance)]
    (.getLayer map-obj layer-id)))

(defn set-paint-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      ;; Convert Clojure maps to JavaScript objects for expressions
      (let [js-value (if (map? value)
                       (clj->js value)
                       value)]
        (try
          (.setPaintProperty map-obj layer-id property-name js-value)
          (catch js/Error e
            (js/console.error (str "Failed to set property " property-name " on layer " layer-id ":") e)
            (throw e)))))))

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
      (cond
        (and (isExpression value) (.-stops value))
        (let [stops (.-stops value)]
          (mapv (fn [[zoom prop-value]]
                  {:zoom zoom
                   :value (if (#{"fill-color" "fill-outline-color" "fill-extrusion-color"} property-name)
                            (parse-color-expression prop-value current-zoom)
                            (parse-numeric-expression prop-value current-zoom))})
                stops))

        :else
        [{:zoom current-zoom
          :value (if (#{"fill-color" "fill-outline-color" "fill-extrusion-color"} property-name)
                   (parse-color-expression value current-zoom)
                   (parse-numeric-expression value current-zoom))}]))))

(defn update-single-value [layer-id property-name new-value]
  "Update a property to a single value (not a stops expression)"
  new-value)

(defn update-zoom-value-pair [layer-id property-name zoom new-value]
  (let [current-value (get-paint-property layer-id property-name)
        current-zoom (get-current-zoom)]
    (cond
      (and (= zoom current-zoom)
           (not (isExpression current-value)))
      new-value

      (and (not= zoom current-zoom)
           (not (isExpression current-value)))
      {:stops [[current-zoom current-value] [zoom new-value]]}

      (and (isExpression current-value) (.-stops current-value))
      (let [stops (.-stops current-value)
            updated-stops (mapv (fn [[stop-zoom stop-value]]
                                  (if (= stop-zoom zoom)
                                    [stop-zoom new-value]
                                    [stop-zoom stop-value]))
                                stops)]
        {:stops updated-stops})

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
