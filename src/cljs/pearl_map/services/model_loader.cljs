(ns pearl-map.services.model-loader
  (:require ["three" :as three]
            ["three/examples/jsm/loaders/GLTFLoader.js" :as GLTFLoaderModule]
            ["three/examples/jsm/loaders/DRACOLoader.js" :as DRACOLoaderModule]))

(defn create-gltf-loader []
  (let [loader (GLTFLoaderModule/GLTFLoader.)
        draco-loader (DRACOLoaderModule/DRACOLoader.)]
    (.setDecoderPath draco-loader "js/libs/draco/")
    (.setDRACOLoader loader draco-loader)
    loader))

(defn load-model [loader url on-load on-error]
  (.load loader url
         (fn [gltf] (on-load gltf))
         nil
         (fn [error] (on-error error))))
