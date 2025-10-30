(ns pearl-map.features.lighting.views
  (:require [re-frame.core :as rf]))

(defn- dispatch-float-update [path raw-value]
  (let [parsed-value (js/parseFloat raw-value)]
    (when-not (js/isNaN parsed-value)
      (rf/dispatch [:lighting/update-property path parsed-value]))))

(defn light-controls []
  (let [light-props @(rf/subscribe [:map/light-properties])]
    (when light-props
      (let [position (:position light-props)
            azimuthal (second position)
            polar (last position)
            desc-style {:font-size "11px", :color "#666", :margin "5px 0 0 0"}]
        [:div {:style {:background "rgba(255,255,255,0.98)"
                       :padding "18px"
                       :border-radius "10px"
                       :font-family "Arial, sans-serif"
                       :width "280px"
                       :box-shadow "0 4px 15px rgba(0,0,0,0.15)"}}
         [:h3 {:style {:margin "0 0 15px 0" :color "#333"}} "Lighting & Shadows"]
         [:div {:key "light-controls-container"}
          [:div {:key "color-control-group" :style {:margin-bottom "15px"}}
           [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Light Color"]
           [:input {:type "color"
                    :value (:color light-props)
                    :on-change #(rf/dispatch [:lighting/update-property [:color] (-> % .-target .-value)])
                    :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"}}]
           [:p {:style desc-style}
            "Color of the main light source."]]

          [:div {:key "intensity-control-group" :style {:margin-bottom "15px"}}
           [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Intensity"]
           [:input {:type "range"
                    :min 0 :max 1 :step 0.05
                    :value (:intensity light-props)
                    :on-change #(dispatch-float-update [:intensity] (-> % .-target .-value))
                    :style {:width "100%"}}]
           [:span {:style {:font-size "12px" :color "#666"}}
            (str "Current: " (.toFixed (:intensity light-props) 2))]
           [:p {:style desc-style}
            "Brightness of the light."]]

          [:div {:key "azimuth-control-group" :style {:margin-bottom "15px"}}
           [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Azimuth"]
           [:input {:type "range"
                    :min 0 :max 360 :step 1
                    :value azimuthal
                    :on-change #(dispatch-float-update [:position 1] (-> % .-target .-value))
                    :style {:width "100%"}}]
           [:span {:style {:font-size "12px" :color "#666"}}
            (str "Current: " azimuthal "°")]
           [:p {:style desc-style}
            "Direction of the light source (like a compass)."]]

          [:div {:key "polar-control-group"}
           [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Polar"]
           [:input {:type "range"
                    :min 0 :max 90 :step 1
                    :value polar
                    :on-change #(dispatch-float-update [:position 2] (-> % .-target .-value))
                    :style {:width "100%"}}]
           [:span {:style {:font-size "12px" :color "#666"}}
            (str "Current: " polar "°")]
           [:p {:style desc-style}
            "Angle from directly overhead (0°) to the horizon (90°)."]]]]))))
