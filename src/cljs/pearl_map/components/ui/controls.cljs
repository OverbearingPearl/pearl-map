(ns pearl-map.components.ui.controls
  (:require [reagent.core :as reagent]
            [pearl-map.components.ui.buttons :as buttons]))

(defn control-panel [props & children]
  (into [:div (-> (if (map? props) props {})
                  (dissoc :position :top :right :bottom :left :z-index :width :background :pointer-events)
                  (update :class #(str "control-panel " (or % ""))))]
        children))

(defn styled-select [{:keys [options] :as props}]
  (into [:select (-> props
                     (dissoc :options :style)
                     (update :class #(str "styled-select " (or % ""))))]
        (for [[option-value option-text] options]
          [:option {:key option-value :value option-value} option-text])))

(defn slider [props]
  [:input (-> props
              (dissoc :style)
              (assoc :type "range")
              (update :class #(str "slider-input " (or % ""))))])

(defn color-picker [props]
  [:input (-> props
              (dissoc :style)
              (assoc :type "color")
              (update :class #(str "color-picker-input " (or % ""))))])
