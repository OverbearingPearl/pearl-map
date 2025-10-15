(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.utils.geometry :as geom]
            [pearl-map.services.model-loader :as model-loader]
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

(defn create-3d-model-layer []
  (let [layer-impl (js-obj)]
    (set! (.-id layer-impl) "3d-model-layer")
    (set! (.-type layer-impl) "custom")
    (set! (.-renderingMode layer-impl) "3d")
    (set! (.-onAdd layer-impl)
          (fn [map gl]
            (let [^js layer-state #js {}
                  ^js context gl
                  ^js scene (three/Scene.)
                  ^js camera (three/PerspectiveCamera. 75 1 0.1 1000)
                  ;; Create a separate canvas for Three.js
                  ^js canvas (js/document.createElement "canvas")
                  ^js renderer (three/WebGLRenderer. #js {:canvas canvas
                                                          :antialias true
                                                          :alpha true})
                  ^js map-obj map]

              ;; Set up canvas and add to map container
              (let [^js container (.getContainer map-obj)
                    width (.-clientWidth container)
                    height (.-clientHeight container)]
                (set! (.-width canvas) width)
                (set! (.-height canvas) height)
                (set! (.-style canvas) (str "position: absolute; top: 0; left: 0; width: " width "px; height: " height "px; pointer-events: none;"))
                (.appendChild container canvas))

              ;; Set up perspective camera
              (let [^js container (.getContainer map-obj)
                    width (.-clientWidth container)
                    height (.-clientHeight container)]
                (set! (.-aspect camera) (/ width height))
                (.updateProjectionMatrix camera)
                ;; Position camera above the scene
                (.set (.-position camera) 0 0 50)
                (.lookAt camera 0 0 0))

              ;; Set up renderer
              (let [^js container (.getContainer map-obj)
                    width (.-clientWidth container)
                    height (.-clientHeight container)]
                (.setSize renderer width height)
                (.setPixelRatio renderer (.-devicePixelRatio js/window))
                ;; Configure for transparent overlay
                (.setClearColor renderer 0x000000 0)
                (set! (.-autoClear renderer) false))

              ;; Handle map resize
              (.on map-obj "resize"
                   (fn []
                     (let [^js container (.getContainer map-obj)
                           width (.-clientWidth container)
                           height (.-clientHeight container)]
                       (set! (.-width canvas) width)
                       (set! (.-height canvas) height)
                       (.setSize renderer width height)
                       (set! (.-aspect camera) (/ width height))
                       (.updateProjectionMatrix camera))))

              ;; Load Eiffel Tower model using model-loader
              (model-loader/load-gltf-model
               "/models/eiffel_tower/scene.gltf"
               (fn [gltf-model]
                 ;; Position the model at Eiffel Tower coordinates
                 (let [model-scene (.-scene gltf-model)]
                   ;; Get model's bounding box for reference (but don't use for scaling)
                   (let [bbox (three/Box3.)
                         center (three/Vector3.)
                         size (three/Vector3.)]
                     (.setFromObject bbox model-scene)
                     (.getCenter bbox center)
                     (.getSize bbox size)
                     (js/console.log "Model original dimensions:" (.-x size) "x" (.-y size) "x" (.-z size)))

                   (.add scene model-scene)
                   (set! (.-model layer-state) gltf-model)))
               (fn [error]
                 (js/console.error "Failed to load 3D model in custom layer:" error)))

              (set! (.-scene layer-state) scene)
              (set! (.-camera layer-state) camera)
              (set! (.-renderer layer-state) renderer)
              (set! (.-canvas layer-state) canvas)
              (set! (.-context layer-state) context)
              (set! (.-map layer-state) map-obj)
              (set! (.-layer-state layer-impl) layer-state))))
    (set! (.-render layer-impl)
          (fn [^js gl ^js matrix]
            (let [^js layer-state (.-layer-state layer-impl)
                  ^js scene (.-scene layer-state)
                  ^js camera (.-camera layer-state)
                  ^js renderer (.-renderer layer-state)
                  ^js map-obj (.-map layer-state)]

              (when (and scene camera renderer (.-model layer-state))
                ;; Get current map view state
                (let [^js center (.getCenter map-obj)
                      ^js zoom (.getZoom map-obj)
                      ^js pitch (.getPitch map-obj)
                      ^js bearing (.getBearing map-obj)

                      ;; Convert Eiffel Tower coordinates to screen position
                      eiffel-point (.project map-obj (clj->js [2.2945 48.8584]))
                      eiffel-x (.-x eiffel-point)
                      eiffel-y (.-y eiffel-point)

                      ;; Get canvas dimensions
                      canvas-width (.-clientWidth (.getContainer map-obj))
                      canvas-height (.-clientHeight (.getContainer map-obj))

                      ;; Convert screen coordinates to normalized device coordinates (-1 to 1)
                      normalized-x (- (* 2 (/ eiffel-x canvas-width)) 1)
                      normalized-y (- (* -2 (/ eiffel-y canvas-height)) 1)

                      ;; PRECISE MERCATOR SCALING: Calculate scale based on actual coordinate system
                      ;; In MapLibre, each zoom level doubles the scale (2^zoom)
                      ;; We want the model to grow when zooming in, shrink when zooming out
                      reference-zoom 15  ;; Model looks good at this zoom level
                      zoom-delta (- zoom reference-zoom)
                      scale-factor (js/Math.pow 2 zoom-delta)]  ;; Direct relationship

                  ;; Position and scale the model - update on every render
                  (let [model-scene (.-scene (.-model layer-state))]
                    ;; Update position
                    (.set (.-position model-scene)
                          (* normalized-x 25)  ;; Reduced scale to make model visible
                          (* normalized-y 25)  ;; Reduced scale to make model visible
                          0)
                    ;; Update scale based on precise mercator scaling
                    (.set (.-scale model-scene) scale-factor scale-factor scale-factor)
                    (js/console.log "Model scale:" scale-factor "at zoom:" zoom))

                  ;; Update camera to look at the scene center
                  (.set (.-position camera) 0 0 50)
                  (.lookAt camera 0 0 0)
                  (.updateProjectionMatrix camera)

                  ;; Render the scene
                  (.render renderer scene camera))))))
    layer-impl))

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
