(ns pearl-map.features.models-3d.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :models-3d/model-loaded
 (fn [db _]
   (:models-3d/model-loaded db)))

(re-frame/reg-sub
 :models-3d/loaded-model
 (fn [db _]
   (:models-3d/loaded-model db)))

(re-frame/reg-sub
 :models-3d/model-load-error
 (fn [db _]
   (:models-3d/model-load-error db)))
