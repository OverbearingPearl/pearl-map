(ns pearl-map.app.subs
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]))

(re-frame/reg-sub
 :map-instance
 (fn [db]
   (:map-instance db)))

(re-frame/reg-sub
 :map-loading?
 (fn [db]
   (:map-loading? db)))

(re-frame/reg-sub
 :current-style-key
 (fn [db]
   (:current-style-key db)))

(re-frame/reg-sub
 :custom-layers
 (fn [db]
   (:custom-layers db)))

(re-frame/reg-sub
 :show-other-components?
 (fn [db]
   (:show-other-components? db)))

(re-frame/reg-sub
 :map/light-properties
 (fn [db]
   (:map/light-properties db)))

(re-frame/reg-sub
 :map/zoom
 (fn [db _]
   (:map/zoom db)))

(re-frame/reg-sub
 :map/prewarming?
 (fn [db]
   (:map/prewarming? db)))
