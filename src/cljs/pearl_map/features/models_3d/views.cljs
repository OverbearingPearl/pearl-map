(ns pearl-map.features.models-3d.views
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.ui.controls :as ui-controls]
            [pearl-map.components.ui.buttons :as ui-buttons]
            [pearl-map.features.map-view.events :as map-events]))

(defn add-example-custom-layer []
  (map-events/add-example-custom-layer))

(defn model-controls []
  (let [model-loaded @(re-frame/subscribe [:models-3d/model-loaded])
        model-error @(re-frame/subscribe [:models-3d/model-load-error])]
    [ui-controls/control-panel
     {:width "240px"}
     [:h3 {:key "title" :style {:margin "0 0 12px 0" :font-size "1.1em" :color "#333"}} "3D Models"]
     [ui-buttons/primary-button {:key "load-button" :on-click #(re-frame/dispatch [:models-3d/load-eiffel-tower])}
      "Load Eiffel Tower"]
     [ui-buttons/success-button {:key "custom-layer" :on-click #(add-example-custom-layer) :style {:margin-top "8px"}}
      "Add Custom Layer"]
     [:div {:key "status" :style {:margin-top "10px" :font-size "12px" :color "#666"}}
      "Status: " [:span {:style {:color (if model-loaded "#28a745" "#dc3545") :font-weight "bold"}}
                  (if model-loaded "Loaded" "Not Loaded")]]
     (when model-error
       [:div {:key "error" :style {:color "#dc3545" :margin-top "8px" :font-size "11px" :background "#f8d7da" :padding "6px" :border-radius "3px"}}
        "Error: " model-error])]))
