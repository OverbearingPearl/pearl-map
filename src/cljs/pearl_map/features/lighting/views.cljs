(ns pearl-map.features.lighting.views
  (:require [re-frame.core :as rf]
            [pearl-map.components.ui.controls :as ui-controls]))

(defn- dispatch-float-update [path raw-value]
  (let [parsed-value (js/parseFloat raw-value)]
    (when-not (js/isNaN parsed-value)
      (rf/dispatch [:lighting/update-property path parsed-value]))))

(defn light-controls []
  (let [light-props @(rf/subscribe [:map/light-properties])]
    (when light-props
      (let [position (:position light-props)
            azimuthal (second position)
            polar (last position)]
        [:div {:class "light-controls"}
         [:h3 {:class "light-controls-title"} "Lighting & Shadows"]
         [:div {:key "light-controls-container"}
          [:div {:key "color-control-group" :class "light-control-group"}
           [:label {:class "light-control-label"} "Light Color"]
           [ui-controls/color-picker {:value (:color light-props)
                                      :on-change #(rf/dispatch [:lighting/update-property [:color] (-> % .-target .-value)])}]
           [:p {:class "light-control-desc"}
            "Color of the main light source."]]

          [:div {:key "intensity-control-group" :class "light-control-group"}
           [:label {:class "light-control-label"} "Intensity"]
           [ui-controls/slider {:min 0 :max 1 :step 0.05
                                :value (:intensity light-props)
                                :on-change #(dispatch-float-update [:intensity] (-> % .-target .-value))}]
           [:span {:class "light-control-value"}
            (str "Current: " (.toFixed (:intensity light-props) 2))]
           [:p {:class "light-control-desc"}
            "Brightness of the light."]]

          [:div {:key "azimuth-control-group" :class "light-control-group"}
           [:label {:class "light-control-label"} "Azimuth"]
           [ui-controls/slider {:min 0 :max 360 :step 1
                                :value azimuthal
                                :on-change #(dispatch-float-update [:position 1] (-> % .-target .-value))}]
           [:span {:class "light-control-value"}
            (str "Current: " azimuthal "°")]
           [:p {:class "light-control-desc"}
            "Direction of the light source (like a compass)."]]

          [:div {:key "polar-control-group" :class "light-control-group"} ;; Added missing class
           [:label {:class "light-control-label"} "Polar"]
           [ui-controls/slider {:min 0 :max 90 :step 1
                                :value polar
                                :on-change #(dispatch-float-update [:position 2] (-> % .-target .-value))}]
           [:span {:class "light-control-value"}
            (str "Current: " polar "°")]
           [:p {:class "light-control-desc"}
            "Angle from directly overhead (0°) to the horizon (90°)."]]]]))))
