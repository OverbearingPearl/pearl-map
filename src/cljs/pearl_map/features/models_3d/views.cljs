(ns pearl-map.features.models-3d.views
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.ui.controls :as ui-controls]
            [pearl-map.components.ui.buttons :as ui-buttons]))

(defn model-controls []
  (let [eiffel-loaded? @(re-frame/subscribe [:models-3d/eiffel-loaded?])]
    [ui-controls/control-panel
     {:width "240px"}
     [:h3 {:key "title" :style {:margin "0 0 12px 0" :font-size "1.1em" :color "#333"}} "3D Models"]
     (if eiffel-loaded?
       [ui-buttons/secondary-button
        {:key "remove-button" :on-click #(re-frame/dispatch [:models-3d/remove-eiffel-tower])}
        "Remove Eiffel Tower"]
       [ui-buttons/primary-button
        {:key "load-button" :on-click #(re-frame/dispatch [:models-3d/add-eiffel-tower])}
        "Load & Display Eiffel Tower"])]))
