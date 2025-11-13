(ns pearl-map.features.style-editor.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :style-editor/target-layer
 (fn [db]
   (get db :style-editor/target-layer)))

(re-frame/reg-sub
 :style-editor/editing-style
 (fn [db]
   (get db :style-editor/editing-style)))

(re-frame/reg-sub
 :style-editor/selected-category
 (fn [db]
   (get db :style-editor/selected-category)))
