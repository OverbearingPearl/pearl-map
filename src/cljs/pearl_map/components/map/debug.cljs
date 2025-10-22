(ns pearl-map.components.map.debug
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.ui.controls :as ui-controls]))

(defn debug-info []
  (let [map-instance @(re-frame/subscribe [:map-instance])
        eiffel-loaded? @(re-frame/subscribe [:models-3d/eiffel-loaded?])]
    [:div {:style {:position "absolute"
                   :bottom "20px"
                   :left "50%"
                   :transform "translateX(-50%)"
                   :z-index 1000
                   :background "rgba(255,255,255,0.95)"
                   :padding "15px"
                   :border-radius "8px"
                   :font-family "Arial, sans-serif"
                   :width "180px"
                   :box-shadow "0 2px 10px rgba(0,0,0,0.1)"
                   :font-size "11px"
                   :pointer-events "auto"}}
     [:div {:style {:margin-bottom "6px"}}
      "Map: " [:span {:style {:color (if map-instance "#28a745" "#dc3545")}} (if map-instance "✓" "✗")]]
     [:div {:style {:margin-bottom "6px"}}
      "Container: " [:span {:style {:color "#28a745"}} "✓"]]
     [:div
      "3D Model: " [:span {:style {:color (if eiffel-loaded? "#28a745" "#dc3545")}} (if eiffel-loaded? "✓" "✗")]]]))
