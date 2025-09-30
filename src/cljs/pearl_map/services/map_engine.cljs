(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.utils.geometry :as geom]
            ["maplibre-gl" :as maplibre]
            ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]))

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
      (.getPaintProperty map-obj layer-id property-name))))

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
      ;; Handle simple values
      (string? color-value)
      (if (.startsWith color-value "#")
        color-value
        (try
          (let [parsed (js/parseFloat color-value)]
            (if (js/isNaN parsed)
              (-> (color color-value)
                  (.hex)
                  (.toString)
                  (.toLowerCase))
              parsed))
          (catch js/Error e
            (-> (color color-value)
                (.hex)
                (.toString)
                (.toLowerCase)))))

      (number? color-value)
      color-value

      ;; Handle expressions with stops (for both colors and numbers)
      (and (object? color-value) (.-stops color-value))
      (let [stops-array (.-stops color-value)
            stops (js->clj stops-array)
            sorted-stops (sort-by first stops)]
        (if (empty? sorted-stops)
          (throw (js/Error. "Empty stops array"))
          (let [first-stop (first sorted-stops)
                last-stop (last sorted-stops)]
            (cond
              (<= current-zoom (first first-stop)) (second first-stop)
              (>= current-zoom (first last-stop)) (second last-stop)
              :else
              (loop [[[z1 v1] & rest-stops] sorted-stops]
                (if (empty? rest-stops)
                  (second first-stop)
                  (let [[z2 v2] (first rest-stops)]
                    (if (and (>= current-zoom z1) (<= current-zoom z2))
                      ;; Interpolate between stops if we're in the range
                      (let [base (or (.-base color-value) 1)
                            t (/ (- current-zoom z1) (- z2 z1))
                            ;; Apply exponential interpolation if base is not 1
                            t' (if (= base 1)
                                 t
                                 (/ (- (js/Math.pow base t) 1) (- base 1)))]
                        (cond
                          ;; Handle numeric values (like opacity)
                          (and (number? v1) (number? v2))
                          (+ v1 (* t' (- v2 v1)))

                          ;; Handle color values
                          (and (string? v1) (string? v2))
                          (let [color1 (color v1)
                                color2 (color v2)
                                mixed (.mix color1 color2 t')]
                            (.string mixed))

                          ;; Fallback to the lower stop's value
                          :else v1))
                      (recur rest-stops)))))))))

      ;; Handle interpolate expressions - treat them the same as stops expressions
      (and (object? color-value) (.-interpolate color-value))
      (let [stops-array (.-stops color-value)
            stops (js->clj stops-array)
            sorted-stops (sort-by first stops)]
        (if (empty? sorted-stops)
          (throw (js/Error. "Empty stops array in interpolate expression"))
          (let [first-stop (first sorted-stops)
                last-stop (last sorted-stops)]
            (cond
              (<= current-zoom (first first-stop)) (second first-stop)
              (>= current-zoom (first last-stop)) (second last-stop)
              :else
              (loop [[[z1 v1] & rest-stops] sorted-stops]
                (if (empty? rest-stops)
                  (second first-stop)
                  (let [[z2 v2] (first rest-stops)]
                    (if (and (>= current-zoom z1) (<= current-zoom z2))
                      ;; Interpolate between stops if we're in the range
                      (let [base (or (.-base color-value) 1)
                            t (/ (- current-zoom z1) (- z2 z1))
                            ;; Apply exponential interpolation if base is not 1
                            t' (if (= base 1)
                                 t
                                 (/ (- (js/Math.pow base t) 1) (- base 1)))]
                        (cond
                          ;; Handle numeric values (like opacity)
                          (and (number? v1) (number? v2))
                          (+ v1 (* t' (- v2 v1)))

                          ;; Handle color values
                          (and (string? v1) (string? v2))
                          (let [color1 (color v1)
                                color2 (color v2)
                                mixed (.mix color1 color2 t')]
                            (.string mixed))

                          ;; Fallback to the lower stop's value
                          :else v1))
                      (recur rest-stops)))))))))

      ;; Handle step expressions
      (and (object? color-value) (.-step color-value))
      (let [input (.-input color-value)
            stops (.-stops color-value)
            default (.-default color-value)]
        (let [value (or default (when stops (aget stops 1)))]
          (if (number? value)
            value
            (js/parseFloat value))))

      ;; Handle simple object values (like numbers wrapped in objects)
      (and (object? color-value) (.-valueOf color-value))
      (let [value (.valueOf color-value)]
        (if (number? value)
          value
          (js/parseFloat value)))

      ;; For other complex expressions, try to extract a reasonable value
      (object? color-value)
      (do
        (js/console.warn "Complex expression detected, attempting to extract value:" (js/JSON.stringify color-value))
        ;; Try various common properties
        (let [value (cond
                      (.-default color-value) (.-default color-value)
                      (.-value color-value) (.-value color-value)
                      :else 0.7)]
          (if (number? value)
            value
            (js/parseFloat value))))

      :else
      (do
        (js/console.warn "Unexpected color value type:" (type color-value) "value:" color-value)
        color-value))))

(defn validate-style [style]
  (try
    (let [complete-style (clj->js
                          {:version 8
                           :name "Building Style Validation"
                           :sources {:dummy-source {:type "geojson"
                                                    :data {:type "FeatureCollection"
                                                           :features []}}}
                           :layers [{:id "validation-layer"
                                     :type "fill"
                                     :source "dummy-source"
                                     :paint style}]})
          validation-result ((.-validateStyleMin style-spec) complete-style)]
      (if (and (array? validation-result) (== (.-length validation-result) 0))
        true
        false))
    (catch js/Error e
      false)))
