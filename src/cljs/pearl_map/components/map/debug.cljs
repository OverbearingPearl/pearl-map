(ns pearl-map.components.map.debug
  (:require [re-frame.core :as re-frame]))

(defn debug-info []
  (let [map-instance @(re-frame/subscribe [:map-instance])
        model-loaded @(re-frame/subscribe [:models-3d/model-loaded])]
    [:div {:style {:position "absolute"
                   :bottom "20px"
                   :left "20px"
                   :z-index 1000
                   :background "rgba(255,255,255,0.9)"
                   :padding "10px"
                   :border-radius "5px"
                   :font-family "Arial, sans-serif"
                   :font-size "12px"}}
     [:div "Map Instance: " (if map-instance "Loaded" "Not Loaded")]
     [:div "Container: " (if (.getElementById js/document "map-container") "Exists" "Missing")]
     [:div "3D Model: " (if model-loaded "Loaded" "Not Loaded")]]))
