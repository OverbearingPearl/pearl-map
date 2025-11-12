(ns pearl-map.components.ui.layout
  (:require [reagent.core :as reagent]))

(defn- component-factory [base-class dissoc-keys]
  (fn [props & children]
    (let [attrs (if (map? props) props {})
          class-name (str base-class " " (or (:class attrs) ""))]
      (into [:div (assoc (apply dissoc attrs dissoc-keys) :class class-name)] children))))

(def flex-container (component-factory "flex-container" [:direction :align :justify :gap :wrap :style]))
(def grid-container (component-factory "grid-container" [:columns :gap :style]))
(def card (component-factory "card" [:padding :background :border-radius :shadow :style :width]))
(def overlay (component-factory "overlay" [:position :top :right :bottom :left :z-index :style]))
