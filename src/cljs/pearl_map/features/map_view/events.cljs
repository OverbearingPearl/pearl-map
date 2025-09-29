(ns pearl-map.features.map-view.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.services.model-loader :as model-loader]))

(defn init-map []
  (let [map-obj (map-engine/init-map)]
    (when map-obj
      (map-engine/on-map-load
       (fn [map-instance]
         (model-loader/load-gltf-model
          "/models/eiffel_tower/scene.gltf"
          (fn [gltf-model]
            (re-frame/dispatch [:set-model-loaded true])
            (re-frame/dispatch [:set-loaded-model gltf-model])
            (set! (.-pearlMapModel js/window) gltf-model)))))
      (map-engine/on-map-error
       (fn [e]
         (js/console.error "Map loading error:" e))))))

(defn change-map-style [style-url]
  (re-frame/dispatch [:set-current-style style-url])
  (map-engine/change-map-style style-url))

(defn add-example-custom-layer []
  (let [custom-layer (map-engine/create-example-custom-layer)]
    (map-engine/add-custom-layer "example-custom-layer" custom-layer nil)))
