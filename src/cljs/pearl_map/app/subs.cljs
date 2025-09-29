(ns pearl-map.app.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :map-instance
 (fn [db]
   (:map-instance db)))

(re-frame/reg-sub
 :current-style
 (fn [db]
   (:current-style db)))

(re-frame/reg-sub
 :custom-layers
 (fn [db]
   (:custom-layers db)))
