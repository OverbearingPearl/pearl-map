(ns pearl-map.features.map-view.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [pearl-map.components.map.container :refer [map-container]]
            [pearl-map.components.map.controls :refer [style-controls]]
            [pearl-map.components.map.debug :refer [debug-info]]
            [pearl-map.features.map-view.events :as map-events]
            [pearl-map.features.style-editor.views :refer [building-style-editor]]
            [pearl-map.features.models-3d.views :refer [model-controls]]))

(defn home-page []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (re-frame/dispatch-sync [:initialize-db])
      (map-events/init-map))
    :reagent-render
    (fn []
      [:div
       [:div {:style {:position "absolute"
                      :top "20px"
                      :left "20px"
                      :zIndex 1000
                      :background "rgba(255,255,255,0.9)"
                      :padding "10px"
                      :borderRadius "5px"
                      :fontFamily "Arial, sans-serif"}}
        [:h1 {:style {:margin 0 :fontSize "1.5em" :color "#333"}} "Pearl Map - Paris 3D"]
        [:p {:style {:margin "5px 0 0 0" :fontSize "0.9em" :color "#666"}}
         "Centered at Eiffel Tower (2.2945°E, 48.8584°N)"]
        [:p {:style {:margin "2px 0 0 0" :fontSize "0.8em" :color "#999"}}
         "Using MapLibre demo vector service"]]
       [style-controls]
       [model-controls]
       [building-style-editor]
       [map-container]
       [debug-info]])}))
