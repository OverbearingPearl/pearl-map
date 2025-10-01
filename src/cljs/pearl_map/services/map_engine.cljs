(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.utils.geometry :as geom]
            ["maplibre-gl" :as maplibre]
            ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]))

;; Simplified expression handling - focus on basic value extraction
(defn- isExpression [x]
  (and (object? x)
       (or (some? (.-stops x))
           (some? (.-property x))
           (some? (.-type x)))))

(defn- evaluate [expr properties]
  ;; Handle expression objects with stops and interpolation
  (cond
    ;; Handle stops expressions with interpolation
    (and (.-stops expr) (.-zoom properties))
    (let [stops (.-stops expr)
          zoom (.-zoom properties)
          base (or (.-base expr) 1)
          stop-count (.-length stops)]

      ;; DEBUG: Log the actual stops structure
      (js/console.log "DEBUG expression stops:"
                      "stops:" (js->clj stops)
                      "zoom:" zoom
                      "base:" base)

      (if (zero? stop-count)
        nil
        ;; Find the appropriate stop range and interpolate
        (loop [i 0]
          (if (< i (dec stop-count))
            (let [current-stop (aget stops i)
                  next-stop (aget stops (inc i))
                  current-zoom-level (aget current-stop 0)
                  current-value (aget current-stop 1)
                  next-zoom-level (aget next-stop 0)
                  next-value (aget next-stop 1)]

              (js/console.log "DEBUG checking stop range:"
                              "i:" i
                              "current-zoom:" current-zoom-level "current-value:" current-value
                              "next-zoom:" next-zoom-level "next-value:" next-value
                              "current-zoom <= zoom:" (<= current-zoom-level zoom)
                              "zoom < next-zoom:" (< zoom next-zoom-level))

              (if (and (<= current-zoom-level zoom)
                       (< zoom next-zoom-level))
                ;; Found the correct zoom range
                ;; CRITICAL FIX: Only interpolate numeric values
                (cond
                  ;; Numeric interpolation
                  (and (number? current-value) (number? next-value))
                  (let [t (/ (- zoom current-zoom-level)
                             (- next-zoom-level current-zoom-level))
                        interpolated (if (= base 1)
                                       ;; Linear interpolation
                                       (+ (* (- 1 t) current-value)
                                          (* t next-value))
                                       ;; Exponential interpolation
                                       (let [zoom-diff (- next-zoom-level current-zoom-level)
                                             base-factor (js/Math.pow base t)]
                                         (+ (* (- 1 base-factor) current-value)
                                            (* base-factor next-value))))]
                    (js/console.log "DEBUG numeric interpolation result:"
                                    "t:" t
                                    "interpolated:" interpolated)
                    interpolated)

                  ;; Color values - use current value (no interpolation)
                  (and (string? current-value) (string? next-value))
                  (do
                    (js/console.log "DEBUG color values, using current:" current-value)
                    current-value)

                  ;; Fallback: use current value
                  :else
                  (do
                    (js/console.log "DEBUG fallback, using current:" current-value)
                    current-value))
                (recur (inc i))))
            ;; Use the last stop if we're beyond all stops
            (let [last-stop (aget stops (dec stop-count))
                  last-value (aget last-stop 1)]
              (js/console.log "DEBUG using last stop:"
                              "last-zoom:" (aget last-stop 0)
                              "last-value:" last-value)
              last-value)))))

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
                                      :fill-opacity 0.7
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
                     (reapply-custom-layers! (:layers current-state)))))))))))

(defn- switch-to-vector-style [current-state style-url]
  (let [^js map-obj (get-map-instance)]
    (clear-custom-layers)
    (.setStyle map-obj style-url)
    (.once map-obj "idle"
           (fn []
             (add-buildings-layer)
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
      ;; Get the raw expression object, not the evaluated value
      (let [raw-value (.getPaintProperty map-obj layer-id property-name)]
        (js/console.log "DEBUG get-paint-property:"
                        "layer:" layer-id
                        "property:" property-name
                        "raw-value:" raw-value
                        "type:" (type raw-value)
                        "isExpression:" (isExpression raw-value)
                        "has-stops:" (and (object? raw-value) (.-stops raw-value)))
        raw-value))))

(defn set-paint-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (.setPaintProperty map-obj layer-id property-name value))))

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

(defn- create-shader-program [context vertex-source fragment-source]
  (let [vertex-shader (.createShader context (.-VERTEX_SHADER context))
        fragment-shader (.createShader context (.-FRAGMENT_SHADER context))
        program (.createProgram context)]
    (.shaderSource context vertex-shader vertex-source)
    (.compileShader context vertex-shader)
    (.shaderSource context fragment-shader fragment-source)
    (.compileShader context fragment-shader)
    (.attachShader context program vertex-shader)
    (.attachShader context program fragment-shader)
    (.linkProgram context program)
    program))

(defn- create-example-geometry [context]
  (let [eiffel-lng 2.2945
        eiffel-lat 48.8584
        [merc-x merc-y merc-z] (geom/calculate-model-position eiffel-lng eiffel-lat 0)
        offset 0.01
        positions (js/Float32Array. [merc-x (+ merc-y offset) merc-z
                                     (- merc-x offset) (- merc-y offset) merc-z
                                     (+ merc-x offset) (- merc-y offset) merc-z])
        position-buffer (.createBuffer context)]
    (.bindBuffer context (.-ARRAY_BUFFER context) position-buffer)
    (.bufferData context (.-ARRAY_BUFFER context) positions (.-STATIC_DRAW context))
    position-buffer))

(defn- setup-layer-rendering [^js layer-impl]
  (set! (.-render layer-impl)
        (fn [^js gl ^js matrix]
          (let [layer-state (.-layer-state layer-impl)
                context (.-context layer-state)
                program (.-program layer-state)
                position-buffer (.-position-buffer layer-state)]
            (when (and context program)
              (.enable context (.-DEPTH_TEST context))
              (.depthFunc context (.-LEQUAL context))
              (.enable context (.-BLEND context))
              (.blendFunc context (.-SRC_ALPHA context) (.-ONE_MINUS_SRC_ALPHA context))
              (.useProgram context program)
              (let [matrix-uniform (.getUniformLocation context program "u_matrix")]
                (.uniformMatrix4fv context matrix-uniform false matrix))
              (.bindBuffer context (.-ARRAY_BUFFER context) position-buffer)
              (let [position-attribute (.getAttribLocation context program "a_pos")]
                (.enableVertexAttribArray context position-attribute)
                (.vertexAttribPointer context position-attribute 3 (.-FLOAT context) false 0 0)
                (.drawArrays context (.-TRIANGLES context) 0 3)))))))

(defn create-example-custom-layer []
  (let [layer-impl (js-obj)]
    (set! (.-id layer-impl) "example-custom-layer")
    (set! (.-type layer-impl) "custom")
    (set! (.-renderingMode layer-impl) "3d")
    (set! (.-onAdd layer-impl)
          (fn [map gl]
            (let [^js layer-state #js {}
                  ^js context gl
                  vertex-source "uniform mat4 u_matrix; attribute vec3 a_pos; void main() { gl_Position = u_matrix * vec4(a_pos, 1.0); }"
                  fragment-source "precision mediump float; void main() { gl_FragColor = vec4(1.0, 0.0, 0.0, 0.5); }"
                  program (create-shader-program context vertex-source fragment-source)
                  position-buffer (create-example-geometry context)]
              (set! (.-position-buffer layer-state) position-buffer)
              (set! (.-program layer-state) program)
              (set! (.-context layer-state) context)
              (set! (.-map layer-state) map)
              (set! (.-layer-state layer-impl) layer-state))))
    (setup-layer-rendering layer-impl)
    layer-impl))

(defn parse-color-expression [color-value current-zoom]
  (when color-value
    (cond
      ;; Handle expression objects
      (isExpression color-value)
      (try
        (let [result (evaluate color-value #js {:zoom current-zoom})]
          (if (string? result)
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
  (when numeric-value
    (js/console.log "DEBUG parse-numeric-expression:"
                    "input:" numeric-value
                    "zoom:" current-zoom
                    "isExpression:" (isExpression numeric-value))

    (cond
      ;; Use the actual expression evaluator for expressions
      (isExpression numeric-value)
      (try
        (let [result (evaluate numeric-value #js {:zoom current-zoom})]
          (js/console.log "DEBUG expression evaluation result:"
                          "zoom:" current-zoom
                          "result:" result
                          "result-type:" (type result))
          (cond
            (number? result) result
            (string? result) (let [parsed (js/parseFloat result)]
                               (if (js/isNaN parsed) nil parsed))
            :else nil))
        (catch js/Error e
          (js/console.warn "Failed to evaluate numeric expression:" e numeric-value)
          nil))

      ;; Handle simple numeric values
      (number? numeric-value)
      numeric-value

      ;; Handle string values
      (string? numeric-value)
      (try
        (let [parsed (js/parseFloat numeric-value)]
          (if (js/isNaN parsed) nil parsed))
        (catch js/Error e
          nil))

      ;; Handle object values with default/value properties
      (object? numeric-value)
      (try
        (let [value (or (.-default numeric-value)
                        (.-value numeric-value)
                        (.-valueOf numeric-value))]
          (cond
            (number? value) value
            (string? value) (let [parsed (js/parseFloat value)]
                              (if (js/isNaN parsed) nil parsed))
            :else nil))
        (catch js/Error e
          nil))

      :else nil)))

(defn validate-style [style]
  (try
    ;; For building paint properties, we don't need full style validation
    ;; Just check if the style map has the expected structure
    (and (map? style)
         (every? (fn [[k v]]
                   (and (keyword? k)
                        (or (string? v)
                            (number? v))))
                 style))
    (catch js/Error e
      false)))
