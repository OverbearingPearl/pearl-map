(ns pearl-map.features.models-3d.views
  (:require [re-frame.core :as re-frame]))

(defn model-controls []
  (let [model-loaded @(re-frame/subscribe [:models-3d/model-loaded])
        model-error @(re-frame/subscribe [:models-3d/model-load-error])]
    [:div {:style {:position "absolute"
                   :top "120px"
                   :right "20px"
                   :z-index 1000
                   :background "rgba(255,255,255,0.9)"
                   :padding "10px"
                   :border-radius "5px"
                   :font-family "Arial, sans-serif"}}
     [:h3 {:style {:margin "0 0 10px 0"}} "3D Models"]
     [:button {:on-click #(re-frame/dispatch [:models-3d/load-eiffel-tower])
               :style {:margin "5px" :padding "8px 12px" :border "none"
                       :border-radius "3px" :background "#007bff" :color "white"
                       :cursor "pointer"}} "Load Eiffel Tower"]
     [:div {:style {:margin-top "10px" :font-size "12px" :color "#666"}}
      "Status: " (if model-loaded "Loaded" "Not Loaded")]
     (when model-error
       [:div {:style {:color "red" :margin-top "10px" :font-size "12px"}}
        "Error: " model-error])]))
