(ns pearl-map.features.models-3d.views
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.ui.controls :as ui-controls]
            [pearl-map.components.ui.buttons :as ui-buttons]))

(defn model-controls []
  (let [eiffel-loaded? @(re-frame/subscribe [:models-3d/eiffel-loaded?])
        eiffel-scale @(re-frame/subscribe [:models-3d/eiffel-scale])]
    [ui-controls/control-panel
     {:width "240px"}
     [:h3 {:key "title" :style {:margin "0 0 12px 0" :font-size "1.1em" :color "#333"}} "3D Models"]
     (if eiffel-loaded?
       [:<> {:key "eiffel-controls"}
        [ui-controls/slider {:key "scale-slider"
                             :label "Scale"
                             :min 0.1
                             :max 5
                             :step 0.1
                             :value eiffel-scale
                             :on-change #(re-frame/dispatch [:models-3d/set-eiffel-scale (js/parseFloat (-> % .-target .-value))])}]
        [ui-buttons/secondary-button
         {:key "remove-button"
          :style {:margin-top "12px"}
          :on-click #(re-frame/dispatch [:models-3d/remove-eiffel-tower])}
         "Remove Eiffel Tower"]]
       [ui-buttons/primary-button
        {:key "load-button" :on-click #(re-frame/dispatch [:models-3d/add-eiffel-tower])}
        "Load & Display Eiffel Tower"])]))
