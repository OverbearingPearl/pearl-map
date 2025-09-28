(ns pearl-map.subs
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
 :model-loaded
 (fn [db]
   (:model-loaded db)))

(re-frame/reg-sub
 :loaded-model
 (fn [db]
   (:loaded-model db)))

(re-frame/reg-sub
 :style-editor/editing-style
 (fn [db]
   (:style-editor/editing-style db)))

(re-frame/reg-sub
 :custom-layers
 (fn [db]
   (:custom-layers db)))
