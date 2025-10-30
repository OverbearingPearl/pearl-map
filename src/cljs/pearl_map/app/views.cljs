(ns pearl-map.app.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [pearl-map.components.map.container :refer [map-container]]
            [pearl-map.features.map-view.events :as map-events]
            [pearl-map.features.map-view.views :refer [map-overlays]]))

(defn home-page []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (re-frame/dispatch-sync [:initialize-db])
      (map-events/init-map))
    :reagent-render
    (fn []
      [:div
       [map-container]
       [map-overlays]])}))
