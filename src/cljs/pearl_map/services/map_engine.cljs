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

(defn create-raster-config []
  (clj->js {:container "map-container"
            :style {:version 8
                    :name "OSM Bright"
                    :center (clj->js eiffel-tower-coords)
                    :zoom 15
                    :pitch 45
                    :bearing 0
                    :sources {:osm {:type "raster"
                                    :tiles ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"]
                                    :tileSize 256
                                    :attribution "© OpenStreetMap contributors"}}
                    :layers [{:id "osm-tiles"
                              :type "raster"
                              :source "osm"
                              :minzoom 0
                              :maxzoom 19}]}
            :attributionControl true
            :maxZoom 19
            :minZoom 0}))

(defn create-vector-config [style-url]
  (clj->js {:container "map-container"
            :style style-url
            :center (clj->js eiffel-tower-coords)
            :zoom 15
            :pitch 45
            :bearing 0
            :attributionControl true
            :maxZoom 19
            :minZoom 0}))

(defn init-map []
  (let [current-style "raster-style"
        map-element (.getElementById js/document "map-container")]
    (when map-element
      (try
        (let [map-config (if (= current-style "raster-style")
                           (create-raster-config)
                           (create-vector-config current-style))
              map-obj (maplibre/Map. map-config)]
          (set-map-instance! map-obj)
          (re-frame/dispatch [:set-map-instance map-obj])
          (.addControl map-obj (maplibre/NavigationControl.))
          (.addControl map-obj (maplibre/ScaleControl.))
          map-obj)
        (catch js/Error e
          nil)))))

(defn on-map-load [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "load" #(callback map-obj))))

(defn on-map-error [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "error" #(callback %))))

(defn add-buildings-layer []
  (when-let [^js map-obj (get-map-instance)]
    (when (and map-obj (not= (.getStyle map-obj) "raster-style"))
      (try
        (.once map-obj "idle"
               (fn []
                 (let [sources (js->clj (.getSources map-obj))]
                   (when (get sources "composite")
                     (try
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
                                              :fill-outline-color "#cccccc"}})))
                       (catch js/Error _))))))
        true
        (catch js/Error _
          false)))))

(defn register-custom-layer [layer-id layer-impl]
  (re-frame/dispatch [:register-custom-layer layer-id layer-impl]))

(defn unregister-custom-layer [layer-id]
  (re-frame/dispatch [:unregister-custom-layer layer-id]))

(defn add-custom-layer [layer-id layer-impl before-id]
  (when-let [^js map-obj (get-map-instance)]
    (try
      (.addLayer map-obj layer-impl before-id)
      (register-custom-layer layer-id layer-impl)
      true
      (catch js/Error e
        false))))

(defn remove-custom-layer [layer-id]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (try
        (.removeLayer map-obj layer-id)
        (unregister-custom-layer layer-id)
        true
        (catch js/Error e
          false)))))

(defn change-map-style [style-url]
  (when-let [^js map-obj (get-map-instance)]
    (try
      (let [current-center (.getCenter map-obj)
            current-zoom (.getZoom map-obj)
            current-pitch (.getPitch map-obj)
            current-bearing (.getBearing map-obj)
            current-layers (get-custom-layers)
            clamped-zoom (cond
                           (= style-url "raster-style") (max 0 (min 19 current-zoom))
                           :else (max 0 (min 22 current-zoom)))]
        (if (= style-url "raster-style")
          (do
            (.remove map-obj)
            (set-map-instance! nil)
            (re-frame/dispatch [:set-map-instance nil])
            (reagent/next-tick
             (fn []
               (init-map)
               (reagent/next-tick
                (fn []
                  (when-let [new-map-obj (get-map-instance)]
                    (.once new-map-obj "load"
                           (fn []
                             (.setCenter new-map-obj current-center)
                             (.setZoom new-map-obj clamped-zoom)
                             (.setPitch new-map-obj current-pitch)
                             (.setBearing new-map-obj current-bearing)
                             (doseq [[layer-id layer-impl] current-layers]
                               (add-custom-layer layer-id layer-impl nil)))))))))
            true)
          (do
            (doseq [[layer-id _] current-layers]
              (remove-custom-layer layer-id))
            (.setStyle map-obj style-url)
            (.once map-obj "idle"
                   (fn []
                     (add-buildings-layer)
                     (doseq [[layer-id layer-impl] current-layers]
                       (add-custom-layer layer-id layer-impl nil))
                     (let [current-zoom-after (.getZoom map-obj)]
                       (when (or (< current-zoom-after 0) (> current-zoom-after 22))
                         (.setZoom map-obj clamped-zoom)))))
            true)))
      (catch js/Error e
        false))))

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

(defn parse-color-expression [color-value current-zoom]
  (try
    (cond
      (and (string? color-value) (.startsWith color-value "#"))
      color-value
      (and (string? color-value) (or (.includes color-value "rgba")
                                     (.includes color-value "rgb")))
      (-> (color color-value)
          (.hex)
          (.toString)
          (.toLowerCase))
      (and (object? color-value) (.-expression color-value))
      (let [expr (.-expression color-value)]
        (if (and (array? expr)
                 (= (aget expr 0) "interpolate")
                 (= (aget expr 1) "linear")
                 (= (aget expr 2) "zoom"))
          (let [stops (drop 3 (array-seq expr))
                stop-pairs (partition 2 stops)]
            (if (empty? stop-pairs)
              (throw (js/Error. "Empty expression stops"))
              (let [first-pair (first stop-pairs)
                    last-pair (last stop-pairs)]
                (cond
                  (<= current-zoom (first first-pair)) (second first-pair)
                  (>= current-zoom (first last-pair)) (second last-pair)
                  :else (loop [[[z1 v1] [z2 v2] & more] stop-pairs]
                          (if (and z2 (>= current-zoom z1) (< current-zoom z2))
                            (let [interpolated-value (+ v1 (* (- current-zoom z1) (/ (- v2 v1) (- z2 z1))))]
                              (if (string? v1)
                                (let [color1 (color v1)
                                      color2 (color v2)
                                      ratio (/ (- current-zoom z1) (- z2 z1))
                                      r (+ (.red color1) (* ratio (- (.red color2) (.red color1))))
                                      g (+ (.green color1) (* ratio (- (.green color2) (.green color1))))
                                      b (+ (.blue color1) (* ratio (- (.blue color2) (.blue color1))))]
                                  (-> (color (clj->js {:r r :g g :b b}))
                                      (.hex)
                                      (.toString)
                                      (.toLowerCase)))
                                interpolated-value))
                            (recur (cons [z2 v2] more))))))))
          (throw (js/Error. (str "Unsupported expression format: " (js/JSON.stringify expr))))))
      (and (object? color-value) (.-stops color-value))
      (let [stops (.-stops color-value)
            sorted-stops (sort-by first (js->clj stops))]
        (if (empty? sorted-stops)
          (throw (js/Error. "Empty stops array"))
          (let [first-stop (first sorted-stops)
                last-stop (last sorted-stops)]
            (cond
              (<= current-zoom (first first-stop)) (second first-stop)
              (>= current-zoom (first last-stop)) (second last-stop)
              :else (loop [[[z1 v1] & rest-stops] sorted-stops]
                      (when (and rest-stops (first (first rest-stops)))
                        (let [[z2 v2] (first rest-stops)]
                          (if (and (>= current-zoom z1) (< current-zoom z2))
                            (let [ratio (/ (- current-zoom z1) (- z2 z1))]
                              (if (string? v1)
                                (let [color1 (color v1)
                                      color2 (color v2)
                                      r (+ (.red color1) (* ratio (- (.red color2) (.red color1))))
                                      g (+ (.green color1) (* ratio (- (.green color2) (.green color1))))
                                      b (+ (.blue color1) (* ratio (- (.blue color2) (.blue color1))))]
                                  (-> (color (clj->js {:r r :g g :b b}))
                                      (.hex)
                                      (.toString)
                                      (.toLowerCase)))
                                (+ v1 (* ratio (- v2 v1)))))
                            (recur rest-stops)))))))))
      (object? color-value)
      (let [str-value (str color-value)]
        (if (.startsWith str-value "#")
          str-value
          (throw (js/Error. (str "Unsupported color object format: " (js/JSON.stringify color-value))))))
      :else
      (throw (js/Error. (str "Invalid color value type: " (type color-value) " - " color-value))))
    (catch js/Error e
      (throw e))))

(defn rgba-to-hex [color-value]
  (try
    (cond
      (and (string? color-value) (.startsWith color-value "#"))
      color-value
      (and (string? color-value) (or (.includes color-value "rgba")
                                     (.includes color-value "rgb")))
      (-> (color color-value)
          (.hex)
          (.toString)
          (.toLowerCase))
      (object? color-value)
      (parse-color-expression color-value (get-current-zoom))
      :else
      (throw (js/Error. (str "Unsupported color format: " color-value))))
    (catch js/Error e
      (throw e))))

(defn hex-to-rgba [hex-str opacity]
  (when hex-str
    (if (.startsWith hex-str "rgba")
      hex-str
      (let [color-obj (color hex-str)
            rgb-obj (.rgb color-obj)
            rgba-obj (.alpha rgb-obj opacity)]
        (.string rgba-obj)))))

(defn create-example-custom-layer []
  (let [layer-impl (js-obj)]
    (set! (.-id layer-impl) "example-custom-layer")
    (set! (.-type layer-impl) "custom")
    (set! (.-renderingMode layer-impl) "3d")
    (set! (.-onAdd layer-impl)
          (fn [map gl]
            (let [^js layer-state #js {}
                  ^js context gl
                  ^js vertex-shader (.createShader context (.-VERTEX_SHADER context))
                  ^js fragment-shader (.createShader context (.-FRAGMENT_SHADER context))
                  ^js program (.createProgram context)]
              (.shaderSource context vertex-shader "
                uniform mat4 u_matrix;
                attribute vec3 a_pos;
                void main() {
                  gl_Position = u_matrix * vec4(a_pos, 1.0);
                }
              ")
              (.compileShader context vertex-shader)
              (.shaderSource context fragment-shader "
                precision mediump float;
                void main() {
                  gl_FragColor = vec4(1.0, 0.0, 0.0, 0.5);
                }
              ")
              (.compileShader context fragment-shader)
              (.attachShader context program vertex-shader)
              (.attachShader context program fragment-shader)
              (.linkProgram context program)
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
                (set! (.-position-buffer layer-state) position-buffer))
              (set! (.-program layer-state) program)
              (set! (.-context layer-state) context)
              (set! (.-map layer-state) map)
              (set! (.-layer-state layer-impl) layer-state))))
    (set! (.-render layer-impl)
          (fn [gl matrix]
            (let [^js layer-state (.-layer-state layer-impl)
                  ^js context (.-context layer-state)
                  ^js program (.-program layer-state)
                  ^js position-buffer (.-position-buffer layer-state)]
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
                  (.drawArrays context (.-TRIANGLES context) 0 3)
                  (let [error (.getError context)]
                    (when (not= error (.-NO_ERROR context)))))))))
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
