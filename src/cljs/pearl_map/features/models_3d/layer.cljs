(ns pearl-map.features.models-3d.layer
  (:require ["three" :as three]
            ["maplibre-gl" :as maplibre]
            [pearl-map.services.model-loader :as model-loader]
            [pearl-map.utils.geometry :as geom]))

(def eiffel-tower-coords [2.2945 48.8584])
(def model-altitude 0)
(def model-rotate [0 0 0])

(defn create-custom-layer []
  (let [model-mercator (.fromLngLat maplibre/MercatorCoordinate
                                    (clj->js eiffel-tower-coords)
                                    model-altitude)
        ;; Use appropriate scale - this is the transform applied to the entire model
        fixed-scale 1.0  ;; Use 1.0 for proper scaling
        model-transform #js {:translateX (.-x model-mercator)
                             :translateY (.-y model-mercator)
                             :translateZ (.-z model-mercator)
                             :rotateX (nth model-rotate 0)
                             :rotateY (nth model-rotate 1)
                             :rotateZ (nth model-rotate 2)
                             :scale fixed-scale}]

    (let [state (atom nil)]
      #js {:id "3d-model-eiffel"
           :type "custom"
           :renderingMode "3d"
           :onAdd (fn [map gl]

                    (let [^js map-obj map
                          ^js canvas (.getCanvas map-obj)
                          ^js scene (three/Scene.)
                          ;; Use wider field of view for better perspective
                          ^js camera (three/PerspectiveCamera. 60 1 1 1000)
                          ^js loader (model-loader/create-gltf-loader)

                          ;; Create directional lights with higher intensity
                          ^js directional-light-1 (three/DirectionalLight. 0xffffff 2)
                          ^js directional-light-2 (three/DirectionalLight. 0xffffff 2)

                          ;; Fix: Use correct canvas and WebGL context
                          ^js renderer (three/WebGLRenderer.
                                        #js {:canvas canvas
                                             :context gl
                                             :antialias true})]

                      ;; Configure lights - position them properly
                      (-> directional-light-1 .-position (.set 1 -1 1))
                      (.add scene directional-light-1)

                      (-> directional-light-2 .-position (.set -1 1 1))
                      (.add scene directional-light-2)

                      ;; Add ambient light with higher intensity for better visibility
                      (let [^js ambient-light (three/AmbientLight. 0x404040 1.0)]
                        (.add scene ambient-light))

                      ;; Load the Eiffel Tower model
                      (model-loader/load-model
                       loader
                       "/models/eiffel_tower/scene.gltf"
                       (fn [gltf]
                         (let [^js model (.-scene gltf)]
                           ;; Position model at origin with proper rotation and scale
                           (-> model .-position (.set 0 0 0))
                           ;; Rotate model to stand upright (90 degrees around X-axis)
                           (-> model .-rotation (.set (/ js/Math.PI 2) 0 0))
                           ;; Use a reasonable scale
                           (-> model .-scale (.set 1 1 1))

                           (.add scene model)))
                       (fn [error]
                         (js/console.error "Failed to load Eiffel Tower model:" error)))

                      (set! (.-autoClear renderer) false)

                      ;; Store state in the atom
                      (reset! state #js {:scene scene
                                         :camera camera
                                         :renderer renderer
                                         :map map-obj
                                         :modelTransform model-transform})))

           :render (fn [gl matrix-data]
                     (js/console.log "=== render called ===")

                     (when-let [^js state @state]
                       (js/console.log "State available:", state)

                       (let [^js scene (.-scene state)
                             ^js camera (.-camera state)
                             ^js renderer (.-renderer state)
                             ^js map-instance (.-map state)
                             ^js model-transform (.-modelTransform state)

                             ;; Fix: Use the correct projection matrix from matrix-data
                             ^js projection-matrix (let [matrix-array (.-projectionMatrix matrix-data)]
                                                     (when matrix-array
                                                       (.fromArray (three/Matrix4.) matrix-array)))]

                         (when projection-matrix
                           (js/console.log "Projection matrix created")

                           ;; Create view-projection matrix with mercator transform applied
                           (let [^js view-proj-matrix (.clone projection-matrix)
                                 ;; Create model matrix for mercator positioning
                                 ^js model-matrix (.makeTranslation
                                                   (three/Matrix4.)
                                                   (.-translateX model-transform)
                                                   (.-translateY model-transform)
                                                   (.-translateZ model-transform))]
                             ;; Apply the model transform to position everything in mercator space
                             (.multiply view-proj-matrix model-matrix)

                             ;; Set camera properties
                             (set! (.-projectionMatrix camera) view-proj-matrix)
                             (set! (.-matrixWorldInverse camera) (.clone view-proj-matrix))

                             ;; Position camera above and in front of the model
                             (-> camera .-position (.set 0 -100 50))
                             ;; Set Z-axis as up to match MapLibre's coordinate system
                             (-> camera .-up (.set 0 0 1))
                             (.lookAt camera (three/Vector3. 0 0 0))

                             ;; Render the scene
                             (.resetState renderer)
                             (.render renderer scene camera)
                             (.triggerRepaint map-instance)
                             (js/console.log "Render completed")))))

                     (js/console.log "=== render finished ==="))})))

;; Test function remains unchanged
(defn test-threejs-rendering []
  (js/console.log "=== Testing Three.js rendering ===")
  (let [^js scene (three/Scene.)
        ^js camera (three/PerspectiveCamera. 75 (/ (.-innerWidth js/window) (.-innerHeight js/window)) 0.1 1000)
        ^js renderer (three/WebGLRenderer.)]

    (.setSize renderer 200 200)
    (-> js/document .-body (.appendChild (.-domElement renderer)))

    ;; Add a simple cube for testing
    (let [^js geometry (three/BoxGeometry. 1 1 1)
          ^js material (three/MeshBasicMaterial. #js {:color 0x00ff00})
          ^js cube (three/Mesh. geometry material)]
      (.add scene cube)
      (.set (.-position camera) 0 0 5)

      (js/console.log "Test cube added to scene")

      ;; Animate the cube - fix recursive call
      (let [animate (fn animate-fn []
                      (js/requestAnimationFrame animate-fn)
                      (set! (.-rotation-x cube) (+ (.-rotation-x cube) 0.01))
                      (set! (.-rotation-y cube) (+ (.-rotation-y cube) 0.01))
                      (.render renderer scene camera))]
        (animate)))))
