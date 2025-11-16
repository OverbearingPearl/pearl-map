(ns pearl-map.app.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [pearl-map.components.map.container :refer [map-container]]
            [pearl-map.app.events :as app-events]
            [pearl-map.features.models-3d.events :as models-3d-events]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.components.map.controls :refer [style-controls]]
            [pearl-map.features.style-editor.views :refer [style-editor]]
            [pearl-map.features.models-3d.views :refer [model-controls]]
            [pearl-map.features.lighting.views :as lighting]))

(defn map-overlays []
  (let [show-other-components? @(re-frame/subscribe [:show-other-components?])]
    [:div {:style {:position "absolute"
                   :top "0"
                   :left "0"
                   :right "0"
                   :bottom "0"
                   :z-index 1000
                   :pointer-events "none"}}

     ;; Top-left corner - Style controls (now includes title content)
     [:div {:style {:position "absolute"
                    :top "20px"
                    :left "20px"
                    :pointer-events "auto"}}
      [style-controls]]

     ;; Conditionally render other components based on state
     (when show-other-components?
       [:div
        ;; Right center - Building style editor
        [:div {:style {:position "absolute"
                       :top "50%"
                       :right "20px"
                       :transform "translateY(-50%)"
                       :pointer-events "auto"}}
         [style-editor]]

        ;; Bottom center - 3D model controls
        [:div {:style {:position "absolute"
                       :bottom "20px"
                       :left "50%"
                       :transform "translateX(-50%)"
                       :pointer-events "auto"}}
         [model-controls]]

        ;; Left center - Lighting controls
        [:div {:style {:position "absolute"
                       :top "50%"
                       :left "20px"
                       :transform "translateY(-50%)"
                       :pointer-events "auto"}}
         [lighting/light-controls]]])

     ;; Bottom-left corner - Reserved for future use
     [:div {:style {:position "absolute"
                    :bottom "20px"
                    :left "20px"
                    :pointer-events "auto"}}
      ;; Future component can be placed here
      ]

     ;; Bottom-right corner - Reserved for future use
     [:div {:style {:position "absolute"
                    :bottom "20px"
                    :right "20px"
                    :pointer-events "auto"}}
      ;; Future component can be placed here
      ]]))

(defn home-page []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (js/console.log "home-page: component-did-mount triggered.")
      (re-frame/dispatch-sync [:initialize-db])
      (app-events/init-map)
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
