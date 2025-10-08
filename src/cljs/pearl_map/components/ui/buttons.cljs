(ns pearl-map.components.ui.buttons
  (:require [reagent.core :as reagent]))

(defn button
  "Base button component with consistent styling"
  [props & children]
  (let [{:keys [on-click style class disabled?] :as attrs} (if (map? props) props {})]
    [:button
     (merge
      {:on-click on-click
       :disabled disabled?
       :style (merge {:margin "5px"
                      :padding "8px 12px"
                      :border "none"
                      :border-radius "3px"
                      :background "#007bff"
                      :color "white"
                      :cursor "pointer"
                      :font-family "Arial, sans-serif"}
                     style)
       :class class}
      (when (map? props)
        (dissoc props :style :class :disabled?)))
     children]))

(defn primary-button [props & children]
  [button (merge {:style {:background "#007bff"}} props) children])

(defn secondary-button [props & children]
  [button (merge {:style {:background "#6c757d"}} props) children])

(defn success-button [props & children]
  [button (merge {:style {:background "#28a745"}} props) children])

(defn danger-button [props & children]
  [button (merge {:style {:background "#dc3545"}} props) children])

(defn dark-button [props & children]
  [button (merge {:style {:background "#343a40"}} props) children])

(defn light-button [props & children]
  [button (merge {:style {:background "#f8f9fa" :color "black"}} props) children])
