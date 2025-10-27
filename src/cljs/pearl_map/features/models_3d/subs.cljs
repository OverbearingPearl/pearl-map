(ns pearl-map.features.models-3d.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :models-3d/eiffel-loaded?
 (fn [db _]
   (get db :models-3d/eiffel-loaded? false)))

(re-frame/reg-sub
 :models-3d/eiffel-scale
 (fn [db _]
   (get db :models-3d/eiffel-scale 1.0)))

(re-frame/reg-sub
 :models-3d/eiffel-rotation-z
 (fn [db _]
   (get db :models-3d/eiffel-rotation-z 0.0)))
