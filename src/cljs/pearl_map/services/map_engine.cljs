(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
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

(defn- interpolate-color-values [color1 color2 ratio]
  (let [c1 (color color1)
        c2 (color color2)
        r (+ (.red c1) (* ratio (- (.red c2) (.red c1))))
        g (+ (.green c1) (* ratio (- (.green c2) (.green c1))))
        b (+ (.blue c1) (* ratio (- (.blue c2) (.blue c1))))]
    (-> (color (clj->js {:r r :g g :b b}))
        (.hex)
        (.toString)
        (.toLowerCase))))

(defn- parse-color-stops [stops current-zoom]
  (let [sorted-stops (sort-by first stops)]
    (cond
      (empty? sorted-stops) nil
      (<= current-zoom (ffirst sorted-stops)) (second (first sorted-stops))
      (>= current-zoom (first (last sorted-stops))) (second (last sorted-stops))
      :else (let [[[z1 v1] [z2 v2]] (loop [[stop & rest-stops] sorted-stops]
                                      (let [next-stop (first rest-stops)]
                                        (if (and (>= current-zoom (first stop)) 
                                                 (< current-zoom (first next-stop)))
                                          [stop next-stop]
                                          (recur rest-stops))))
                  ratio (/ (- current-zoom z1) (- z2 z1))]
              (if (string? v1)
                (interpolate-color-values v1 v2 ratio)
                (+ v1 (* ratio (- v2 v1))))))))

(defn- parse-expression-color [color-value current-zoom]
  (let [expr (.-expression color-value)]
    (if (and (array? expr)
             (= (aget expr 0) "interpolate")
             (= (aget expr 1) "linear")
             (= (aget expr 2) "zoom"))
      (parse-color-stops (partition 2 (drop 3 (array-seq expr))) current-zoom)
      (throw (js/Error. (str "Unsupported expression format: " (js/JSON.stringify expr)))))))

(defn- parse-stops-color [color-value current-zoom]
  (parse-color-stops (js->clj (.-stops color-value)) current-zoom))

(defn- parse-string-color [color-value]
  (cond
    (.startsWith color-value "#") color-value
    (or (.includes color-value "rgba") (.includes color-value "rgb"))
    (-> (color color-value) (.hex) (.toString) (.toLowerCase))
    :else (throw (js/Error. (str "Invalid color string: " color-value)))))

(defn parse-color-expression [color-value current-zoom]
  (cond
    (string? color-value) (parse-string-color color-value)
    (.-expression color-value) (parse-expression-color color-value current-zoom)
    (.-stops color-value) (parse-stops-color color-value current-zoom)
    :else (throw (js/Error. (str "Invalid color value: " color-value)))))

(defn rgba-to-hex [color-value]
  (parse-color-expression color-value (get-current-zoom)))

(defn hex-to-rgba [hex-str opacity]
  (when hex-str
    (if (.startsWith hex-str "rgba")
      hex-str
      (let [color-obj (color hex-str)
            rgb-obj (.rgb color-obj)
            rgba-obj (.alpha rgb-obj opacity)]
        (.string rgba-obj)))))

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
        lng-lat #js {:lng eiffel-lng :lat eiffel-lat}
        mercator-coords (.fromLngLat maplibre/MercatorCoordinate lng-lat 0)
        merc-x (.-x mercator-coords)
        merc-y (.-y mercator-coords)
        merc-z (.-z mercator-coords)
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
