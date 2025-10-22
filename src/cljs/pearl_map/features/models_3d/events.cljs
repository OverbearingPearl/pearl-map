(ns pearl-map.features.models-3d.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.features.models-3d.layer :as model-layer]
            [pearl-map.services.map-engine :as map-engine]))

(re-frame/reg-event-db
 :models-3d/set-eiffel-loaded
 (fn [db [_ loaded?]]
   (assoc db :models-3d/eiffel-loaded? loaded?)))

(re-frame/reg-event-fx
 :models-3d/add-eiffel-tower
 (fn [{:keys [db]} _]
   (let [custom-layer (model-layer/create-custom-layer)]
     (map-engine/add-custom-layer
      "3d-model-eiffel"
      (clj->js custom-layer)
      nil)
     {:db (assoc db :models-3d/eiffel-loaded? true)})))

(re-frame/reg-event-fx
 :models-3d/remove-eiffel-tower
 (fn [{:keys [db]} _]
   (map-engine/remove-custom-layer "3d-model-eiffel")
   {:db (assoc db :models-3d/eiffel-loaded? false)}))
