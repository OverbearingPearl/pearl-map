(ns pearl-map.features.models-3d.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.features.models-3d.layer :as model-layer]
            [pearl-map.services.map-engine :as map-engine]))

(re-frame/reg-event-db
 :models-3d/set-eiffel-loaded
 (fn [db [_ loaded?]]
   (assoc db :models-3d/eiffel-loaded? loaded?)))

(re-frame/reg-event-fx
 :models-3d/set-eiffel-scale
 (fn [{:keys [db]} [_ scale]]
   (model-layer/set-scale scale)
   {:db (assoc db :models-3d/eiffel-scale scale)}))

(re-frame/reg-event-fx
 :models-3d/set-eiffel-rotation-z
 (fn [{:keys [db]} [_ degrees]]
   (model-layer/set-rotation-z degrees)
   {:db (assoc db :models-3d/eiffel-rotation-z degrees)}))

(re-frame/reg-event-fx
 :models-3d/add-eiffel-tower
 (fn [{:keys [db]} _]
   (js/console.log "models-3d/add-eiffel-tower event dispatched.")
   (let [initial-scale (:models-3d/eiffel-scale db)
         initial-rotation-z (:models-3d/eiffel-rotation-z db)
         custom-layer (model-layer/create-custom-layer initial-scale initial-rotation-z)]
     (map-engine/add-custom-layer
      "3d-model-eiffel"
      (clj->js custom-layer)
      nil)
     {:db (assoc db :models-3d/eiffel-loaded? true)})))

(re-frame/reg-event-fx
 :models-3d/remove-eiffel-tower
 (fn [{:keys [db]} _]
   (map-engine/remove-custom-layer "3d-model-eiffel")
   (model-layer/cleanup-state)
   {:db (assoc db :models-3d/eiffel-loaded? false)}))

(re-frame/reg-event-fx
 :models-3d/fly-to-eiffel-tower
 (fn [_ _]
   (map-engine/fly-to-location model-layer/eiffel-tower-coords 16)
   {}))
