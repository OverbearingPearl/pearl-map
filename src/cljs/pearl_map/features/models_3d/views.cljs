(ns pearl-map.features.models-3d.views
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.ui.controls :as ui-controls]
            [pearl-map.components.ui.buttons :as ui-buttons]))

(defn model-controls []
  (let [eiffel-loaded? @(re-frame/subscribe [:models-3d/eiffel-loaded?])
        eiffel-scale @(re-frame/subscribe [:models-3d/eiffel-scale])
        eiffel-rotation-z @(re-frame/subscribe [:models-3d/eiffel-rotation-z])]
    [ui-controls/control-panel
     {:width "240px"}
     [:h3 {:key "title" :class "model-controls-title"} "3D Models"]
     (if eiffel-loaded?
       [:<> {:key "eiffel-controls"}
        [ui-controls/slider {:key "scale-slider"
                             :label "Scale"
                             :min 0.1
                             :max 5
                             :step 0.1
                             :value eiffel-scale
                             :on-change #(re-frame/dispatch [:models-3d/set-eiffel-scale (js/parseFloat (-> % .-target .-value))])}]
        [:div {:key "scale-value"
               :class "model-controls-value"}
         (str "x " (-> eiffel-scale js/Number (.toFixed 1)))]

        [ui-controls/slider {:key "rotation-slider"
                             :label "Rotation (Z)"
                             :min 0
                             :max 360
                             :step 1
                             :value eiffel-rotation-z
                             :on-change #(re-frame/dispatch [:models-3d/set-eiffel-rotation-z (js/parseFloat (-> % .-target .-value))])}]
        [:div {:key "rotation-value"
               :class "model-controls-value"}
         (str (-> eiffel-rotation-z js/Number (.toFixed 0)) "°")]

        [:div {:class "model-controls-buttons" :style {:margin-top "1rem" :display "flex" :flex-direction "column" :gap "0.5rem"}}
         [ui-buttons/secondary-button
          {:key "fly-to-button"
           :on-click #(re-frame/dispatch [:models-3d/fly-to-eiffel-tower])}
          "Fly to Eiffel Tower"]
         [ui-buttons/secondary-button
          {:key "remove-button"
           :class "model-controls-remove-button"
           :on-click #(re-frame/dispatch [:models-3d/remove-eiffel-tower])}
          "Remove Eiffel Tower"]]]
       [ui-buttons/primary-button
        {:key "load-button" :on-click #(re-frame/dispatch [:models-3d/add-eiffel-tower])}
        "Load & Display Eiffel Tower"])]))
