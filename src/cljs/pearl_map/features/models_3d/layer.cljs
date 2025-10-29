(ns pearl-map.features.models-3d.layer
  (:require ["three" :as three]
            ["maplibre-gl" :as maplibre]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [pearl-map.app.db :as app-db]
            [pearl-map.services.model-loader :as model-loader]
            [pearl-map.utils.geometry :as geom]))

(defn- maplibre-light-pos->three-direction
  "Converts MapLibre light position [r, a, p] to a Three.js direction Vector3.
  It assumes the map is on the XZ plane and Y is up.
  - MapLibre's azimuth 'a' is clockwise from North (-Z direction).
  - MapLibre's polar 'p' is from zenith (+Y direction)."
  [[r a p]]
  (let [a-rad (* a (/ js/Math.PI 180))
        p-rad (* p (/ js/Math.PI 180))
        x (* r (js/Math.sin p-rad) (js/Math.sin a-rad))
        y (* r (js/Math.cos p-rad))
        z (* r (- (js/Math.sin p-rad)) (js/Math.cos a-rad))]
    (three/Vector3. x y z)))

(def eiffel-tower-coords [2.2945 48.8584])
(def model-altitude 0)
(def model-rotate [(/ js/Math.PI 2) 0 0])
(def eiffel-tower-real-height 330) ;; Real height of Eiffel Tower in meters

(defonce layer-state (atom nil))

(defn set-scale [scale]
  (when-let [^js st @layer-state]
    (set! (.-userScale st) scale)))

(defn set-rotation-z [degrees]
  (when-let [^js st @layer-state]
    (set! (.-userRotationZ st) (* degrees (/ js/Math.PI 180)))))

(defn cleanup-state []
  (reset! layer-state nil))

(defn create-custom-layer [initial-scale initial-rotation-z]
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
                        ^js directional-light (three/DirectionalLight. 0xffffff)
                        ^js ambient-light (three/AmbientLight. 0xffffff 0.4)
                        ^js renderer (three/WebGLRenderer.
                                      #js {:canvas canvas
                                           :context gl
                                           :antialias true})]

                    (.add scene directional-light)
                    (.add scene ambient-light)

                    ;; Load the Eiffel Tower model
                    (model-loader/load-model
                     loader
                     "/models/eiffel_tower/scene.glb"
                     (fn [gltf]
                       (let [model-scene (.-scene gltf)
                             box (three/Box3.)
                             size (three/Vector3.)]
                         (.setFromObject box model-scene)
                         (.getSize box size)
                         (let [model-unit-height (.-y size)
                               model-scale-factor (if (pos? model-unit-height)
                                                    (/ eiffel-tower-real-height model-unit-height)
                                                    1)]
                           (.add scene model-scene)
                           (when-let [^js st @layer-state]
                             (set! (.-modelScaleFactor st) model-scale-factor)))))
                     (fn [error]
                       (js/console.error "Failed to load Eiffel Tower model:" error)))

                    (set! (.-autoClear renderer) false)

                    ;; Dispatch events to reset sliders to default values.
                    (rf/dispatch [:models-3d/set-eiffel-scale (:models-3d/eiffel-scale app-db/default-db)])
                    (rf/dispatch [:models-3d/set-eiffel-rotation-z (:models-3d/eiffel-rotation-z app-db/default-db)])

                    ;; Store state in the atom, using default values
                    (reset! layer-state
                            #js {:scene scene
                                 :camera camera
                                 :renderer renderer
                                 :light directional-light
                                 :map map-obj
                                 :modelTransform model-transform
                                 :userScale (:models-3d/eiffel-scale app-db/default-db)
                                 :userRotationZ (* (:models-3d/eiffel-rotation-z app-db/default-db) (/ js/Math.PI 180))
                                 :modelScaleFactor 1})))

         :render (fn [gl matrix-data]
                   (when-let [^js state @layer-state]
                     (let [light-props (:map/light-properties @rf-db/app-db)
                           light (.-light state)
                           _ (when (and light-props light)
                               (doto light
                                 (-> .-color (.set (three/Color. (:color light-props))))
                                 (-> .-position (.copy (maplibre-light-pos->three-direction (:position light-props))))
                                 (aset "intensity" (:intensity light-props))))
                           user-scale (.-userScale state)
                           scene (.-scene state)
                           camera (.-camera state)
                           renderer (.-renderer state)
                           map-instance (.-map state)
                           model-transform (.-modelTransform state)
                           model-scale-factor (or (.-modelScaleFactor state) 1)
                           final-scale (* (.-scale model-transform) model-scale-factor user-scale)
                           user-rotation-z (or (.-userRotationZ state) 0)
                           rotation-x (.makeRotationAxis (three/Matrix4.) (three/Vector3. 1 0 0) (.-rotateX model-transform))
                           rotation-y (.makeRotationAxis (three/Matrix4.) (three/Vector3. 0 1 0) (.-rotateY model-transform))
                           rotation-z (.makeRotationAxis (three/Matrix4.) (three/Vector3. 0 0 1) (+ (.-rotateZ model-transform) user-rotation-z))
                           m (.fromArray (three/Matrix4.) (-> matrix-data .-defaultProjectionData .-mainMatrix))
                           l (-> (three/Matrix4.)
                                 (.makeTranslation (.-translateX model-transform)
                                                   (.-translateY model-transform)
                                                   (.-translateZ model-transform))
                                 (.scale (three/Vector3. final-scale
                                                         (- final-scale)
                                                         final-scale))
                                 (.multiply rotation-z)
                                 (.multiply rotation-y)
                                 (.multiply rotation-x))]
                       (set! (.-projectionMatrix camera) (.multiply m l))
                       (.resetState renderer)
                       (.render renderer scene camera)
                       (.triggerRepaint map-instance))))}))
