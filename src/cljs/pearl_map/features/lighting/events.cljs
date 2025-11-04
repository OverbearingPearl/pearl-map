(ns pearl-map.features.lighting.events
  (:require [re-frame.core :as rf]))

(rf/reg-event-fx
 :lighting/update-property
 (fn [{:keys [db]} [_ path value]]
   (let [new-db (assoc-in db (into [:map/light-properties] path) value)
         light-props (:map/light-properties new-db)]
     {:db new-db
      :fx [[:set-map-light light-props]
           [:dispatch [:buildings/update-shadow light-props]]]})))
