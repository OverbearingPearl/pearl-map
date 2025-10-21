(ns pearl-map.features.models-3d.views
  (:require [re-frame.core :as re-frame]
            [pearl-map.components.ui.controls :as ui-controls]
            [pearl-map.components.ui.buttons :as ui-buttons]))

(defn model-controls []
  [ui-controls/control-panel
   {:width "240px"}
   [:h3 {:key "title" :style {:margin "0 0 12px 0" :font-size "1.1em" :color "#333"}} "3D Models"]
   [ui-buttons/primary-button {:key "load-button" :on-click #()}  ;; Empty click handler
    "Load & Display Eiffel Tower"]])
