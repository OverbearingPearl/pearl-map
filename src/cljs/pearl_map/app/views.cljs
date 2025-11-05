(ns pearl-map.app.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [pearl-map.components.map.container :refer [map-container]]
            [pearl-map.features.map-view.events :as map-events]
            [pearl-map.features.map-view.views :refer [map-overlays]]
            [pearl-map.features.models-3d.events :as models-3d-events]
            [pearl-map.services.map-engine :as map-engine]))

(defn home-page []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (js/console.log "home-page: component-did-mount triggered.")
      (re-frame/dispatch-sync [:initialize-db])
      (map-events/init-map)
      (js/console.log "home-page: Calling map-engine/on-map-load.")
      (map-engine/on-map-load
       (fn [_]
         (js/console.log "home-page: map-engine/on-map-load callback triggered. Dispatching :models-3d/add-eiffel-tower.")
         (re-frame/dispatch [:models-3d/add-eiffel-tower]))))
    :reagent-render
    (fn []
      [:div
       [map-container]
       [map-overlays]])}))
