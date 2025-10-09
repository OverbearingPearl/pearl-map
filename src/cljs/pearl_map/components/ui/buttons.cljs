(ns pearl-map.components.ui.buttons
  (:require [reagent.core :as reagent]))

(defn button
  "Base button component with consistent styling"
  [props & children]
  (let [{:keys [on-click style class disabled?] :as attrs} (if (map? props) props {})
        base-style {:margin "5px"
                    :padding "8px 12px"
                    :border "none"
                    :border-radius "3px"
                    :background "#007bff"
                    :color "white"
                    :cursor "pointer"
                    :font-family "Arial, sans-serif"}]
    [:button
     (merge
      {:on-click on-click
       :disabled disabled?
       :style (merge base-style (when (map? props) (:style props)) style)
       :class class}
      (when (map? props)
        (dissoc props :style :class :disabled?)))
     children]))

(defn primary-button [props & children]
  [button (update props :style merge {:background "#007bff"}) children])

(defn secondary-button [props & children]
  [button (update props :style merge {:background "#6c757d"}) children])

(defn success-button [props & children]
  [button (update props :style merge {:background "#28a745"}) children])

(defn danger-button [props & children]
  [button (update props :style merge {:background "#dc3545"}) children])

(defn dark-button [props & children]
  [button (update props :style merge {:background "#343a40"}) children])

(defn light-button [props & children]
  [button (update props :style merge {:background "#f8f9fa" :color "black"}) children])
