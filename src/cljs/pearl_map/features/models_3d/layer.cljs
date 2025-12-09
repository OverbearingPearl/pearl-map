(ns pearl-map.features.models-3d.layer
  (:require ["three" :as three]
            ["maplibre-gl" :as maplibre]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [reagent.ratom :as ratom]
            [pearl-map.app.db :as app-db]
            [pearl-map.config :as config]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.services.model-loader :as model-loader]
            [pearl-map.services.map-adapter :as map-adapter]))

(defn- update-light-from-props [^js light map-light-props]
  (when-let [three-light-props (map-adapter/convert-light-props map-light-props)]
    (doto light
      (-> .-color (.set (:color three-light-props)))
      (-> .-position (.copy (:position three-light-props)))
      (set! -intensity (:intensity three-light-props)))))

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
  (let [plane (three/Mesh. (three/PlaneGeometry. 2000 2000)
                           (three/ShadowMaterial. #js {:opacity 0.3}))]
    (set! (.-receiveShadow plane) true)
    (set! (.-x (.-rotation plane)) (* -90 (/ js/Math.PI 180)))
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
      (set! -near 0.1)
      (set! -far 1000)
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
      (/ config/eiffel-tower-real-height model-unit-height)
      1)))

(defn create-custom-layer
  ([] (create-custom-layer config/eiffel-tower-initial-scale config/eiffel-tower-initial-rotation-z))
  ([initial-scale initial-rotation-z]
   (let [;; Local mutable state for this layer instance only
         layer-state (volatile! nil)
         ;; Watcher to trigger repaints when DB changes
         repaint-watcher (atom nil)]
     #js {:id config/model-layer-id
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
                      "models/eiffel_tower/scene.glb"
                      (fn [gltf]
                        (let [^three/Object3D model-scene (.-scene gltf)
                              _ (configure-shadow-camera directional-light model-scene)
                              _ (enable-model-shadows model-scene)
                              model-scale-factor (calculate-model-scale-factor model-scene)]
                          (.add scene model-scene)
                          (when-let [^js st @layer-state]
                            (set! (.-modelScaleFactor st) model-scale-factor))
                          (.triggerRepaint map-obj)))
                      (fn [error]
                        (js/console.error "Failed to load Eiffel Tower model:" error)))

                     ;; Sync initial values to DB
                     (rf/dispatch [:models-3d/set-eiffel-scale initial-scale])
                     (rf/dispatch [:models-3d/set-eiffel-rotation-z initial-rotation-z])

                     ;; Store imperative state
                     (vreset! layer-state
                              #js {:scene scene
                                   :camera camera
                                   :light directional-light
                                   :map map-obj
                                   :modelScaleFactor 1
                                   :renderer nil})

                     ;; Setup Reactive Repaint Trigger
                     (let [r (ratom/reaction
                              {:scale (:models-3d/eiffel-scale @rf-db/app-db)
                               :rotation-z (:models-3d/eiffel-rotation-z @rf-db/app-db)
                               :light (:map/light-properties @rf-db/app-db)})]
                       (reset! repaint-watcher r)
                       (add-watch r :layer-repaint
                                  (fn [_ _ _ _]
                                    (.triggerRepaint map-obj))))))

          :onRemove (fn [_ _]
                      (when-let [r @repaint-watcher]
                        (remove-watch r :layer-repaint))
                      (when-let [^js state @layer-state]
                        (when-let [renderer (.-renderer state)]
                          (.dispose renderer))
                        (set! (.-renderer state) nil)
                        (set! (.-scene state) nil)
                        (set! (.-camera state) nil))
                      (vreset! layer-state nil))

          :render (fn [gl matrix-data]
                    (when-let [^js state @layer-state]
                      (let [db @rf-db/app-db
                            light-props (:map/light-properties db)]

                        (update-light-from-props (.-light state) light-props)

                        (let [scene (.-scene state)
                              camera (.-camera state)
                              renderer (or (.-renderer state)
                                           (let [new-renderer (init-renderer (.getCanvas (.-map state)) gl)]
                                             (set! (.-renderer state) new-renderer)
                                             new-renderer))
                              ^js map-instance (.-map state)
                              canvas (.getCanvas map-instance)
                              model-transform-params {:lng-lat config/eiffel-tower-coords
                                                      :altitude config/model-altitude
                                                      :rotation-rad {:x (nth config/model-rotate 0)
                                                                     :y (+ (nth config/model-rotate 1) (* (:models-3d/eiffel-rotation-z db) (/ js/Math.PI 180)))
                                                                     :z (nth config/model-rotate 2)}
                                                      :scale (* (:models-3d/eiffel-scale db) (.-modelScaleFactor state))}
                              model-matrix (map-adapter/get-model-matrix model-transform-params)
                              camera-matrix-params {:map-projection-matrix (-> matrix-data .-defaultProjectionData .-mainMatrix)
                                                    :model-matrix model-matrix}
                              final-matrix (map-adapter/get-camera-matrix camera-matrix-params)]

                          (set! (.-projectionMatrix camera) final-matrix)
                          (.setSize renderer (.-width canvas) (.-height canvas) false)
                          (.resetState renderer)
                          (.render renderer scene camera)
                          (.triggerRepaint map-instance)))))})))

(defn reload! []
  (let [db @rf-db/app-db
        current-scale (:models-3d/eiffel-scale db)
        current-rot-deg (:models-3d/eiffel-rotation-z db)
        new-layer (create-custom-layer current-scale current-rot-deg)]

    ;; 1. Always update the registry with the NEW layer definition immediately.
    ;; This ensures that if the map is re-initializing (e.g. due to React remount),
    ;; it will pick up the new layer code when it loads via on-map-loaded.
    (map-engine/register-custom-layer config/model-layer-id new-layer)

    ;; 2. Handle the active map instance if it exists and has the layer
    (when-let [^js map-obj (map-engine/get-map-instance)]
      (when (.getLayer map-obj config/model-layer-id)
        ;; Remove from map ONLY (don't use map-engine/remove-custom-layer which unregisters from DB)
        (.removeLayer map-obj config/model-layer-id)

        ;; 3. Re-add to map after cleanup cycle
        (js/requestAnimationFrame
         (fn []
           (js/requestAnimationFrame
            (fn []
              ;; Fetch the map instance again to ensure we have the current one
              ;; (The map might have been destroyed/recreated during the RAF delay)
              (when-let [^js current-map (map-engine/get-map-instance)]
                ;; Use engine to add (handles safety checks and re-registration)
                (map-engine/add-custom-layer
                 current-map
                 config/model-layer-id
                 new-layer
                 nil)
                (.triggerRepaint current-map))))))))))
