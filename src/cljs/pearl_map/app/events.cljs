(ns pearl-map.app.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.app.db :as db]))

(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

(re-frame/reg-event-db
 :set-map-instance
 (fn [db [_ map-instance]]
   (assoc db :map-instance map-instance)))

(re-frame/reg-event-db
 :set-current-style
 (fn [db [_ style-url]]
   (assoc db :current-style style-url)))

(re-frame/reg-event-db
 :set-model-loaded
 (fn [db [_ loaded?]]
   (assoc db :model-loaded loaded?)))

(re-frame/reg-event-db
 :set-loaded-model
 (fn [db [_ model]]
   (assoc db :loaded-model model)))

(re-frame/reg-event-db
 :register-custom-layer
 (fn [db [_ layer-id layer-impl]]
   (update db :custom-layers assoc layer-id layer-impl)))

(re-frame/reg-event-db
 :unregister-custom-layer
 (fn [db [_ layer-id]]
   (update db :custom-layers dissoc layer-id)))

(re-frame/reg-event-db
 :clear-custom-layers
 (fn [db _]
   (assoc db :custom-layers {})))
