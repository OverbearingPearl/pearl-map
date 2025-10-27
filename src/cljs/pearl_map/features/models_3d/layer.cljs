(ns pearl-map.features.models-3d.layer
  (:require ["three" :as three]
            ["maplibre-gl" :as maplibre]
            [pearl-map.services.model-loader :as model-loader]
            [pearl-map.utils.geometry :as geom]))

(def eiffel-tower-coords [2.2945 48.8584])
(def model-altitude 0)
(def model-rotate [(/ js/Math.PI 2) 0 0])

(defonce layer-state (atom nil))

(defn set-scale [scale]
  (when-let [^js st @layer-state]
    (set! (.-userScale st) scale)))

(defn cleanup-state []
  (reset! layer-state nil))

(defn create-custom-layer [initial-scale]
  (let [model-mercator (.fromLngLat maplibre/MercatorCoordinate
                                    (clj->js eiffel-tower-coords)
                                    model-altitude)
        base-model-scale (.meterInMercatorCoordinateUnits model-mercator)
        model-transform #js {:translateX (.-x model-mercator)
                             :translateY (.-y model-mercator)
                             :translateZ (.-z model-mercator)
                             :rotateX (nth model-rotate 0)
                             :rotateY (nth model-rotate 1)
                             :rotateZ (nth model-rotate 2)
                             :scale base-model-scale}]

    #js {:id "3d-model-eiffel"
         :type "custom"
         :renderingMode "3d"
         :onAdd (fn [map gl]
                  (let [^js map-obj map
                        ^js canvas (.getCanvas map-obj)
                        ^js scene (three/Scene.)
                        ^js camera (three/Camera.)
                        ^js loader (model-loader/create-gltf-loader)
                        ^js directional-light-1 (three/DirectionalLight. 0xffffff)
                        ^js directional-light-2 (three/DirectionalLight. 0xffffff)
                        ^js renderer (three/WebGLRenderer.
                                      #js {:canvas canvas
                                           :context gl
                                           :antialias true})]

                    (-> directional-light-1 .-position (.set 0 -70 100) (.normalize))
                    (.add scene directional-light-1)

                    (-> directional-light-2 .-position (.set 0 70 100) (.normalize))
                    (.add scene directional-light-2)

                    ;; Load the Eiffel Tower model
                    (model-loader/load-model
                     loader
                     "/models/eiffel_tower/scene.gltf"
                     (fn [gltf]
                       (.add scene (.-scene gltf)))
                     (fn [error]
                       (js/console.error "Failed to load Eiffel Tower model:" error)))

                    (set! (.-autoClear renderer) false)

                    ;; Store state in the atom
                    (reset! layer-state #js {:scene scene
                                             :camera camera
                                             :renderer renderer
                                             :map map-obj
                                             :modelTransform model-transform
                                             :userScale initial-scale})))

         :render (fn [gl matrix-data]
                   (when-let [^js state @layer-state]
                     (let [user-scale (.-userScale state)
                           scene (.-scene state)
                           camera (.-camera state)
                           renderer (.-renderer state)
                           map-instance (.-map state)
                           model-transform (.-modelTransform state)
                           final-scale (* (.-scale model-transform) user-scale)
                           rotation-x (.makeRotationAxis (three/Matrix4.) (three/Vector3. 1 0 0) (.-rotateX model-transform))
                           rotation-y (.makeRotationAxis (three/Matrix4.) (three/Vector3. 0 1 0) (.-rotateY model-transform))
                           rotation-z (.makeRotationAxis (three/Matrix4.) (three/Vector3. 0 0 1) (.-rotateZ model-transform))
                           m (.fromArray (three/Matrix4.) (-> matrix-data .-defaultProjectionData .-mainMatrix))
                           l (-> (three/Matrix4.)
                                 (.makeTranslation (.-translateX model-transform)
                                                   (.-translateY model-transform)
                                                   (.-translateZ model-transform))
                                 (.scale (three/Vector3. final-scale
                                                         (- final-scale)
                                                         final-scale))
                                 (.multiply rotation-x)
                                 (.multiply rotation-y)
                                 (.multiply rotation-z))]
                       (set! (.-projectionMatrix camera) (.multiply m l))
                       (.resetState renderer)
                       (.render renderer scene camera)
                       (.triggerRepaint map-instance))))}))

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
