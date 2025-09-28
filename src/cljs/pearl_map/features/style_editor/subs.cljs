(ns pearl-map.features.style-editor.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :style-editor/editing-style
 (fn [db]
   (:style-editor/editing-style db)))

(re-frame/reg-sub
 :style-editor/editing-style-value
 (fn [db [_ key]]
   (get-in db [:style-editor/editing-style key])))
