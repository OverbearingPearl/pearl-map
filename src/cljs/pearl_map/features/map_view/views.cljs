(ns pearl-map.features.map-view.views
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.map.controls :refer [style-controls]]
            [pearl-map.components.map.debug :refer [debug-info]]
            [pearl-map.features.style-editor.views :refer [building-style-editor]]
            [pearl-map.features.models-3d.views :refer [model-controls]]))

(defn map-overlays []
  [:div {:style {:position "absolute"
                 :top "0"
                 :left "0"
                 :right "0"
                 :bottom "0"
                 :z-index 1000
                 :pointer-events "none"}}

   ;; Top center - Title area
   [:div {:style {:position "absolute"
                  :top "20px"
                  :left "50%"
                  :transform "translateX(-50%)"
                  :pointer-events "auto"}}
    [:div {:style {:background "rgba(255,255,255,0.95)"
                   :padding "15px"
                   :border-radius "8px"
                   :font-family "Arial, sans-serif"
                   :max-width "280px"
                   :box-shadow "0 2px 10px rgba(0,0,0,0.1)"}}
     [:h1 {:style {:margin "0 0 8px 0" :font-size "1.4em" :color "#333" :line-height "1.2"}}
      "Pearl Map - Paris 3D"]
     [:p {:style {:margin "0 0 4px 0" :font-size "0.85em" :color "#666" :line-height "1.3"}}
      "Centered at Eiffel Tower"]
     [:p {:style {:margin "0" :font-size "0.75em" :color "#999" :line-height "1.2"}}
      "(2.2945°E, 48.8584°N)"]]]

   ;; Right center - Building style editor
   [:div {:style {:position "absolute"
                  :top "50%"
                  :right "20px"
                  :transform "translateY(-50%)"
                  :pointer-events "auto"}}
    [building-style-editor]]

   ;; Bottom center - Debug information
   [:div {:style {:position "absolute"
                  :bottom "20px"
                  :left "50%"
                  :transform "translateX(-50%)"
                  :pointer-events "auto"}}
    [debug-info]]

   ;; Left center - 3D model controls
   [:div {:style {:position "absolute"
                  :top "50%"
                  :left "20px"
                  :transform "translateY(-50%)"
                  :pointer-events "auto"}}
    [model-controls]]

   ;; Top-left corner - Style controls
   [:div {:style {:position "absolute"
                  :top "20px"
                  :left "20px"
                  :pointer-events "auto"}}
    [style-controls]]

   ;; Top-right corner - Reserved for future use
   [:div {:style {:position "absolute"
                  :top "20px"
                  :right "20px"
                  :pointer-events "auto"}}
    ;; Future component can be placed here
    ]

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
    ]])
