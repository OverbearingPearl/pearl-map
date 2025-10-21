(ns pearl-map.features.models-3d.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.model-loader :as model-loader]
            [pearl-map.services.map-engine :as map-engine]))

(re-frame/reg-event-db
 :models-3d/set-model-loaded
 (fn [db [_ loaded?]]
   (assoc db :models-3d/model-loaded loaded?)))

(re-frame/reg-event-db
 :models-3d/set-loaded-model
 (fn [db [_ model]]
   (assoc db :models-3d/loaded-model model)))

(re-frame/reg-event-db
 :models-3d/set-model-load-error
 (fn [db [_ error]]
   (assoc db :models-3d/model-load-error error)))

(re-frame/reg-event-db
 :models-3d/clear-model-load-error
 (fn [db _]
   (dissoc db :models-3d/model-load-error)))

(re-frame/reg-event-fx
 :models-3d/load-eiffel-tower
 (fn [{:keys [db]} _]
   (re-frame/dispatch [:models-3d/clear-model-load-error])
   (model-loader/load-gltf-model
    "/models/eiffel_tower/scene.gltf"
    (fn [gltf-model]
      (re-frame/dispatch [:models-3d/set-model-loaded true])
      (re-frame/dispatch [:models-3d/set-loaded-model gltf-model])
      (re-frame/dispatch [:models-3d/clear-model-load-error])
      ;; Automatically add the 3D model layer after successful load
      (let [custom-layer (map-engine/create-3d-model-layer)]
        (map-engine/add-custom-layer "3d-model-layer" custom-layer nil))
      (set! (.-pearlMapModel js/window) gltf-model))
    (fn [error]
      (re-frame/dispatch [:models-3d/set-model-load-error (str "Model loading failed: " error)])
      (re-frame/dispatch [:models-3d/set-model-loaded false])))
   {:db db}))
