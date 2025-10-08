(ns pearl-map.components.ui.controls
  (:require [reagent.core :as reagent]
            [pearl-map.components.ui.buttons :as buttons]))

(defn control-panel
  "Base control panel component with consistent styling"
  [props & children]
  (let [{:keys [position top right bottom left z-index width background pointer-events] :as attrs} (if (map? props) props {})]
    [:div
     (merge
      {:style {:background (or background "rgba(255,255,255,0.95)")
               :padding "15px"
               :border-radius "8px"
               :font-family "Arial, sans-serif"
               :width width
               :box-shadow "0 2px 10px rgba(0,0,0,0.1)"
               :pointer-events (or pointer-events "auto")}}
      (when (map? props)
        (dissoc props :position :top :right :bottom :left :z-index :width :background :pointer-events)))
     children]))

(defn styled-select
  "Styled select dropdown component"
  [{:keys [value on-change options style] :as props}]
  [:select
   (merge
    {:value value
     :on-change on-change
     :style (merge {:width "100%"
                    :padding "5px"
                    :border "1px solid #ddd"
                    :border-radius "4px"}
                   style)}
    (dissoc props :options))
   (for [[option-value option-text] options]
     [:option {:key option-value :value option-value} option-text])])

(defn slider
  "Styled slider component"
  [{:keys [value on-change min max step style] :as props}]
  [:input
   (merge
    {:type "range"
     :value value
     :on-change on-change
     :min (or min "0")
     :max (or max "1")
     :step (or step "0.1")
     :style (merge {:width "100%"} style)}
    (dissoc props :style))])

(defn color-picker
  "Styled color picker component"
  [{:keys [value on-change disabled? style] :as props}]
  [:input
   (merge
    {:type "color"
     :value value
     :on-change on-change
     :disabled disabled?
     :style (merge {:width "100%"
                    :height "30px"
                    :border "1px solid #ddd"
                    :border-radius "4px"
                    :background "transparent"}
                   style)}
    (dissoc props :style))])
