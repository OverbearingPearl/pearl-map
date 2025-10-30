(ns pearl-map.features.map-view.views
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.map.controls :refer [style-controls]]
            [pearl-map.features.style-editor.views :refer [building-style-editor]]
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
         [building-style-editor]]

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
