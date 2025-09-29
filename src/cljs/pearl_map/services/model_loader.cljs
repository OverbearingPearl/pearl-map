(ns pearl-map.services.model-loader
  (:require ["three/addons/loaders/GLTFLoader.js" :as GLTFLoader]
            [re-frame.core :as re-frame]))

(defonce gltf-loader (new (.-GLTFLoader GLTFLoader)))

(defn load-gltf-model
  "Load a GLTF model from the given URL and call the success callback with the loaded model.
   Handles errors and dispatches appropriate re-frame events."
  [url success-callback error-callback]
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
           (re-frame/dispatch [:models-3d/set-model-load-error (str "Model loading failed: " error)])
           (when error-callback (error-callback error)))))

;; Error handling utilities
(defn clear-model-errors []
  "Clear any model loading errors"
  (re-frame/dispatch [:models-3d/clear-model-load-error]))
