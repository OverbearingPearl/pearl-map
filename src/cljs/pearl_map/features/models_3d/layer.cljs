(ns pearl-map.features.models-3d.layer
  (:require ["three" :as three]
            ["maplibre-gl" :as maplibre]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [pearl-map.app.db :as app-db]
            [pearl-map.services.model-loader :as model-loader]
            [pearl-map.features.models-3d.model-data :as model-data]
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

(defn- update-light-from-props [^js light light-props]
  (when (and light-props light)
    (doto light
      (-> .-color (.set (three/Color. (:color light-props))))
      (-> .-position (.copy (maplibre-light-pos->three-direction (:position light-props))))
      (set! -intensity (:intensity light-props)))))

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

(defn- make-model-transform [coords altitude model-rotate]
  (let [model-mercator (.fromLngLat maplibre/MercatorCoordinate (clj->js coords) altitude)
        base-model-scale (.meterInMercatorCoordinateUnits model-mercator)]
    #js {:translateX (.-x model-mercator)
         :translateY (.-y model-mercator)
         :translateZ (.-z model-mercator)
         :rotateX (nth model-rotate 0)
         :rotateY (nth model-rotate 1)
         :rotateZ (nth model-rotate 2)
         :scale base-model-scale}))

(defn- init-renderer [^js canvas ^js gl]
  (doto (three/WebGLRenderer. #js {:canvas canvas :context gl :antialias true})
    (set! -autoClear false)
    (-> .-shadowMap (doto (set! -enabled true) (set! -type three/PCFSoftShadowMap)))))

(defn- init-lights []
  (let [directional-light (doto (three/DirectionalLight. 0xffffff)
                            (set! -castShadow true)
                            (-> .-shadow .-camera (doto (set! -far 5000) (set! -near 1)))
                            (-> .-shadow .-mapSize (doto (set! -width 2048) (set! -height 2048))))
        ambient-light (three/AmbientLight. 0xffffff 0.4)]
    {:directional directional-light
     :ambient ambient-light}))

(defn- add-shadow-plane [^js scene]
  (let [plane (doto (three/Mesh. (three/PlaneGeometry. 2000 2000)
                                 (three/ShadowMaterial. #js {:opacity 0.3}))
                (set! -receiveShadow true)
                (-> .-rotation (set! -x (* -90 (/ js/Math.PI 180)))))]
    (.add scene plane)))

(defn- configure-shadow-camera [^js directional-light ^js model-scene]
  (let [size (-> (three/Box3.)
                 (.setFromObject model-scene)
                 (.getSize (three/Vector3.)))
        shadow-camera (.-camera (.-shadow directional-light))
        max-dimension (max (.-x size) (.-y size) (.-z size))
        frustum-half-size (* max-dimension 0.6)]
    (doto shadow-camera
      (set! -left (- frustum-half-size))
      (set! -right frustum-half-size)
      (set! -top frustum-half-size)
      (set! -bottom (- frustum-half-size))
      (.updateProjectionMatrix))))

(defn- enable-model-shadows [^js model-scene]
  (.traverse model-scene
             (fn [^js object]
               (when (.-isMesh object)
                 (doto object
                   (set! -castShadow true)
                   (set! -receiveShadow true)))))
  model-scene)

(defn- calculate-model-scale-factor [^js model-scene]
  (let [size (-> (three/Box3.)
                 (.setFromObject model-scene)
                 (.getSize (three/Vector3.)))
        model-unit-height (.-y size)]
    (if (pos? model-unit-height)
      (/ eiffel-tower-real-height model-unit-height)
      1)))

(defn- calculate-local-transform-matrix [^js model-transform final-scale user-rotation-z]
  (let [rotation-x (.makeRotationAxis (three/Matrix4.) (three/Vector3. 1 0 0) (.-rotateX model-transform))
        rotation-y (.makeRotationAxis (three/Matrix4.) (three/Vector3. 0 1 0) (.-rotateY model-transform))
        rotation-z (.makeRotationAxis (three/Matrix4.) (three/Vector3. 0 0 1) (+ (.-rotateZ model-transform) user-rotation-z))]
    (-> (three/Matrix4.)
        (.makeTranslation (.-translateX model-transform)
                          (.-translateY model-transform)
                          (.-translateZ model-transform))
        (.scale (three/Vector3. final-scale
                                (- final-scale)
                                final-scale))
        (.multiply rotation-z)
        (.multiply rotation-y)
        (.multiply rotation-x))))

(defn- get-render-params [^js state]
  (let [model-transform (.-modelTransform state)
        model-scale-factor (or (.-modelScaleFactor state) 1)
        user-scale (.-userScale state)
        final-scale (* (.-scale model-transform) model-scale-factor user-scale)
        user-rotation-z (or (.-userRotationZ state) 0)]
    {:final-scale final-scale
     :user-rotation-z user-rotation-z
     :model-transform model-transform}))

(defn create-custom-layer [initial-scale initial-rotation-z]
  (let [model-transform (make-model-transform eiffel-tower-coords model-altitude model-rotate)]
    #js {:id "3d-model-eiffel"
         :type "custom"
         :renderingMode "3d"
         :onAdd (fn [map gl]
                  (let [^js map-obj map
                        ^js scene (three/Scene.)
                        ^js camera (three/Camera.)
                        ^js loader (model-loader/create-gltf-loader)
                        lights (init-lights)
                        directional-light (:directional lights)]

                    (.add scene directional-light)
                    (.add scene (:ambient lights))
                    (add-shadow-plane scene)

                    (model-loader/load-model
                     loader
                     model-data/eiffel-tower-model-data
                     (fn [gltf]
                       (let [^three/Object3D model-scene (.-scene gltf)
                             _ (configure-shadow-camera directional-light model-scene)
                             _ (enable-model-shadows model-scene)
                             model-scale-factor (calculate-model-scale-factor model-scene)]
                         (.add scene model-scene)
                         (when-let [^js st @layer-state]
                           (set! (.-modelScaleFactor st) model-scale-factor))))
                     (fn [error]
                       (js/console.error "Failed to load Eiffel Tower model:" error)))

                    ;; Dispatch events to reset sliders to default values.
                    (rf/dispatch [:models-3d/set-eiffel-scale (:models-3d/eiffel-scale app-db/default-db)])
                    (rf/dispatch [:models-3d/set-eiffel-rotation-z (:models-3d/eiffel-rotation-z app-db/default-db)])

                    ;; Store state in the atom, using default values
                    (reset! layer-state
                            #js {:scene scene
                                 :camera camera
                                 :light directional-light
                                 :map map-obj
                                 :modelTransform model-transform
                                 :userScale (:models-3d/eiffel-scale app-db/default-db)
                                 :userRotationZ (* (:models-3d/eiffel-rotation-z app-db/default-db) (/ js/Math.PI 180))
                                 :modelScaleFactor 1})))
         :onRemove (fn [_ _]
                     (cleanup-state))
         :render (fn [gl matrix-data]
                   (when-let [^js state @layer-state]
                     (update-light-from-props (.-light state) (:map/light-properties @rf-db/app-db))
                     (let [scene (.-scene state)
                           camera (.-camera state)
                           renderer (or (.-renderer state)
                                        (let [new-renderer (init-renderer (.getCanvas (.-map state)) gl)]
                                          (set! (.-renderer state) new-renderer)
                                          new-renderer))
                           map-instance (.-map state)
                           {:keys [final-scale user-rotation-z model-transform]} (get-render-params state)
                           m (.fromArray (three/Matrix4.) (-> matrix-data .-defaultProjectionData .-mainMatrix))
                           l (calculate-local-transform-matrix model-transform final-scale user-rotation-z)]
                       (set! (.-projectionMatrix camera) (.multiply m l))
                       (.resetState renderer)
                       (.render renderer scene camera)
                       (.triggerRepaint map-instance))))}))
