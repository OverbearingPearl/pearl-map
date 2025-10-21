(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.utils.geometry :as geom]
            ["maplibre-gl" :as maplibre]
            ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]
            ["three" :as three]))

;; Simplified expression handling - focus on basic value extraction
(defn isExpression [x]
  (and (object? x)
       (or (some? (.-stops x))
           (some? (.-property x))
           (some? (.-type x)))))

(defn- evaluate [expr properties]
  (cond
    ;; Handle stops expressions with interpolation
    (and (.-stops expr) (.-zoom properties))
    (let [stops-array (.-stops expr)
          ;; Convert to Clojure vector for easier processing
          stops (vec (js->clj stops-array))
          zoom (.-zoom properties)
          base (or (.-base expr) 1)
          stop-count (count stops)]

      (if (zero? stop-count)
        nil
        ;; Find the appropriate stop range
        (loop [i 0]
          (cond
            (>= i stop-count)
            ;; Beyond all stops - use last value
            (second (nth stops (dec stop-count)))

            :else
            (let [[current-zoom current-value] (nth stops i)]
              (if (<= zoom current-zoom)
                ;; Found the stop range
                (if (zero? i)
                  ;; Before first stop - use first value
                  current-value
                  ;; Interpolate between previous and current stop
                  (let [[prev-zoom prev-value] (nth stops (dec i))
                        t (/ (- zoom prev-zoom) (- current-zoom prev-zoom))
                        ;; Apply exponential interpolation if base != 1
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

    ;; Handle literal values
    (.-value expr) (.-value expr)
    (.-default expr) (.-default expr)

    ;; Handle direct values
    :else expr))

(def eiffel-tower-coords [2.2945 48.8584])

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
    (.once map-obj "idle"
           (fn []
             (when (get (js->clj (.getSources map-obj)) "composite")
               (when-not (.getLayer map-obj "buildings")
                 (.addLayer map-obj
                            (clj->js
                             {:id "buildings"
                              :type "fill"
                              :source "composite"
                              :source-layer "building"
                              :filter ["==" "extrude" "true"]
                              :paint {:fill-color "#f0f0f0"
                                      :fill-opacity 1.0
                                      :fill-outline-color "#cccccc"}}))))))))

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
    {:center (.getCenter map-obj)
     :zoom (.getZoom map-obj)
     :pitch (.getPitch map-obj)
     :bearing (.getBearing map-obj)
     :layers (get-custom-layers)}))

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
      ;; Handle "transparent" string directly
      (and (string? color-value) (= color-value "transparent"))
      "transparent"

      ;; Handle expression objects
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

      ;; Handle simple color strings
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

      ;; Handle numeric values (convert to hex)
      (number? color-value)
      (try
        (str "#" (.toString (js/Math.floor color-value) 16))
        (catch js/Error e
          (js/console.warn "Failed to convert numeric to color:" color-value)
          nil))

      ;; Handle simple object values
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

    ;; Handle simple numeric values first
    (number? numeric-value) numeric-value

    ;; Handle all expression types through the evaluate function
    (isExpression numeric-value)
    (try
      (let [result (evaluate numeric-value #js {:zoom current-zoom})]
        (js/console.log "Evaluated numeric expression:" numeric-value "->" result)
        (cond
          (number? result) result
          (string? result) (let [parsed (js/parseFloat result)]
                             (if (js/isNaN parsed) nil parsed))
          :else nil))
      (catch js/Error e
        (js/console.warn "Failed to evaluate numeric expression:" e)
        nil))

    ;; Handle string values
    (string? numeric-value) (let [parsed (js/parseFloat numeric-value)]
                              (if (js/isNaN parsed) nil parsed))

    ;; Handle object values - check for common expression properties
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
        ;; Handle stops expressions
        (and (isExpression value) (.-stops value))
        (let [stops (.-stops value)]
          (mapv (fn [[zoom prop-value]]
                  {:zoom zoom
                   :value (if (#{"fill-color" "fill-outline-color"} property-name)
                            (parse-color-expression prop-value current-zoom)
                            (parse-numeric-expression prop-value current-zoom))})
                stops))

        ;; Handle single values
        :else
        [{:zoom current-zoom
          :value (if (#{"fill-color" "fill-outline-color"} property-name)
                   (parse-color-expression value current-zoom)
                   (parse-numeric-expression value current-zoom))}]))))

(defn update-single-value [layer-id property-name new-value]
  "Update a property to a single value (not a stops expression)"
  new-value)

(defn update-zoom-value-pair [layer-id property-name zoom new-value]
  (let [current-value (get-paint-property layer-id property-name)
        current-zoom (get-current-zoom)]
    (cond
      ;; If we're updating the current zoom level and it's a single value, keep it as single value
      (and (= zoom current-zoom)
           (not (isExpression current-value)))
      new-value

      ;; If we're updating a different zoom level and it's currently a single value,
      ;; convert to stops expression with original value at current zoom and new value at target zoom
      (and (not= zoom current-zoom)
           (not (isExpression current-value)))
      {:stops [[current-zoom current-value] [zoom new-value]]}

      ;; If it's already a stops expression, update the specific stop
      (and (isExpression current-value) (.-stops current-value))
      (let [stops (.-stops current-value)
            updated-stops (mapv (fn [[stop-zoom stop-value]]
                                  (if (= stop-zoom zoom)
                                    [stop-zoom new-value]
                                    [stop-zoom stop-value]))
                                stops)]
        {:stops updated-stops})

      ;; For any other case (like other expression types), just use the new value
      :else
      new-value)))

(defn validate-style [style]
  (try
    ;; Accept expression objects, strings, numbers, and nil values
    (and (map? style)
         (every? (fn [[k v]]
                   (and (keyword? k)
                        (or (string? v)
                            (number? v)
                            (nil? v)
                            ;; Allow expression objects
                            (and (object? v)
                                 (or (some? (.-stops v))
                                     (some? (.-property v))
                                     (some? (.-type v))))
                            ;; Allow Clojure maps that represent expressions
                            (and (map? v)
                                 (or (:stops v)
                                     (:property v)
                                     (:type v))))))
                 style))
    (catch js/Error e
      false)))
