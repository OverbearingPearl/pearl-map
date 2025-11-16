(ns pearl-map.features.lighting.events
  (:require [re-frame.core :as rf]
            [pearl-map.services.map-engine :as map-engine]
            ["maplibre-gl" :as maplibre]))

(rf/reg-event-fx
 :lighting/update-property
 (fn [{:keys [db]} [_ path value]]
   (let [new-db (assoc-in db (into [:map/light-properties] path) value)
         light-props (:map/light-properties new-db)]
     (when-let [^maplibre/Map map-instance (:map-instance db)]
       (.setLight map-instance (clj->js light-props)))
     {:db new-db
      :dispatch [:buildings/update-shadow light-props]})))
