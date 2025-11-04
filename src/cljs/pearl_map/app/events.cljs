(ns pearl-map.app.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.app.db :as db]
            [pearl-map.features.lighting.events]))

(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

(re-frame/reg-event-db
 :set-map-instance
 (fn [db [_ map-instance]]
   (assoc db :map-instance map-instance)))

(re-frame/reg-event-fx
 :set-current-style-key
 (fn [{:keys [db]} [_ style-key]]
   (let [default-light-props (:map/light-properties db/default-db)
         default-building-style (:style-editor/editing-style db/default-db)
         new-db (-> db
                    (assoc :current-style-key style-key)
                    (assoc :map/light-properties default-light-props)
                    (assoc :style-editor/editing-style default-building-style))]
     {:db new-db
      :fx [[:set-map-light default-light-props]]})))

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

(re-frame/reg-event-db
 :toggle-other-components
 (fn [db _]
   (update db :show-other-components? not)))
