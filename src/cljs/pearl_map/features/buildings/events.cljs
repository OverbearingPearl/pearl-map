(ns pearl-map.features.buildings.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.features.buildings.layer :as buildings-layer]))

(re-frame/reg-event-fx
 :buildings/add-layers
 (fn [_ _]
   (buildings-layer/add-extruded-building-layers)
   {}))

(re-frame/reg-event-fx
 :buildings/update-shadow
 (fn [_ [_ light-props]]
   (buildings-layer/update-extrusion-shadow-by-light light-props)
   {}))
