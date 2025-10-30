(ns pearl-map.services.model-loader
  (:require ["three" :as three]
            ["three/examples/jsm/loaders/GLTFLoader.js" :as GLTFLoaderModule]))

(defn create-gltf-loader []
  (GLTFLoaderModule/GLTFLoader.))

(defn load-model [loader url on-load on-error]
  (.load loader url
         (fn [gltf] (on-load gltf))
         nil
         (fn [error] (on-error error))))
