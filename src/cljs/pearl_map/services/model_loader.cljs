(ns pearl-map.services.model-loader
  (:require ["three" :as three]
            ["three/addons/loaders/GLTFLoader.js" :as GLTFLoader]
            [re-frame.core :as re-frame]))

(defonce gltf-loader (new (.-GLTFLoader GLTFLoader)))

(defn load-gltf-model
  "Load a GLTF model from the given URL and call the success callback with the loaded model.
   Handles errors and dispatches appropriate re-frame events."
  [url success-callback]
  (js/console.log (str "Loading GLTF model from: " url))

  (.load gltf-loader
         url
         (fn [gltf]
           (js/console.log "GLTF model loaded successfully")
           (success-callback gltf))
         (fn [progress]
           (js/console.log "Loading progress:" progress))
         (fn [error]
           (js/console.error "Failed to load GLTF model:" error)
           (re-frame/dispatch [:models-3d/set-model-load-error (str "Model loading failed: " error)]))))

;; Model positioning and scaling utilities
(defn position-model-at-coordinates
  "Position a 3D model at specific geographic coordinates"
  [model longitude latitude elevation]
  ;; This would need to be implemented based on your coordinate system
  ;; For now, just set a basic position
  (set! (.-position model) (three/Vector3. 0 elevation 0))
  model)

(defn scale-model-to-size
  "Scale the model to an appropriate size for the scene"
  [model scale-factor]
  (set! (.-scale model) (three/Vector3. scale-factor scale-factor scale-factor))
  model)

;; Model management functions
(defn add-model-to-scene
  [scene gltf-model]
  (when (and scene gltf-model (.-scene gltf-model))
    (.add scene (.-scene gltf-model))
    (js/console.log "Model added to scene")))

(defn remove-model-from-scene
  [scene gltf-model]
  (when (and scene gltf-model (.-scene gltf-model))
    (.remove scene (.-scene gltf-model))
    (js/console.log "Model removed from scene")))

;; Animation and interaction utilities
(defn setup-model-animation
  [gltf-model]
  (when (and gltf-model (.-animations gltf-model) (> (.-length (.-animations gltf-model)) 0))
    (js/console.log "Model animations available:" (.-length (.-animations gltf-model)))
    ))

;; Error handling utilities
(defn clear-model-errors []
  "Clear any model loading errors"
  (re-frame/dispatch [:clear-model-load-error]))
