(ns pearl-map.services.threejs
  (:require ["three/examples/jsm/loaders/GLTFLoader.js" :refer [GLTFLoader]]))

;; GLTF loader function with proper error handling
(defn load-gltf-model [model-path callback]
  (let [loader (GLTFLoader.)]
    (.load loader
           model-path
           (fn [gltf]
             (js/console.log "GLTF model loaded successfully:" gltf)
             (callback gltf))
           (fn [progress]
             ;; Log loading progress for debugging
             (js/console.log "Loading progress:" (.-loaded progress) "/" (.-total progress)))
           (fn [error]
             ;; Handle loading errors with detailed logging
             (js/console.error "Error loading GLTF model:" error)
             (js/console.error "Error details:" (.-message error))))))
