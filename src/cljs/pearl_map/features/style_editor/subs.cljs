(ns pearl-map.features.style-editor.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :style-editor/target-layer
 (fn [db]
   (get db :style-editor/target-layer "building")))

(re-frame/reg-sub
 :style-editor/editing-style
 (fn [db]
   (get db :style-editor/editing-style {:fill-color "#f0f0f0"
                                        :fill-opacity 1.0
                                        :fill-outline-color "#cccccc"
                                        :fill-extrusion-color "#f0f0f0"})))

(re-frame/reg-sub
 :style-editor/editing-style-value
 (fn [db [_ key]]
   (get-in db [:style-editor/editing-style key])))
