(ns pearl-map.components.ui.layout
  (:require [reagent.core :as reagent]))

(defn flex-container
  "Flex container component"
  [props & children]
  (let [{:keys [direction align justify gap wrap style] :as attrs} (if (map? props) props {})]
    [:div
     (merge
      {:style (merge {:display "flex"
                      :flex-direction (or direction "row")
                      :align-items (or align "center")
                      :justify-content (or justify "flex-start")
                      :gap (or gap "10px")
                      :flex-wrap (or wrap "wrap")}
                     style)}
      (when (map? props)
        (dissoc props :direction :align :justify :gap :wrap :style)))
     children]))

(defn grid-container
  "Grid container component"
  [props & children]
  (let [{:keys [columns gap style] :as attrs} (if (map? props) props {})]
    [:div
     (merge
      {:style (merge {:display "grid"
                      :grid-template-columns (or columns "repeat(auto-fit, minmax(200px, 1fr))")
                      :gap (or gap "10px")}
                     style)}
      (when (map? props)
        (dissoc props :columns :gap :style)))
     children]))

(defn card
  "Card component for content containers"
  [props & children]
  (let [{:keys [padding background border-radius shadow style] :as attrs} (if (map? props) props {})]
    [:div
     (merge
      {:style (merge {:padding (or padding "15px")
                      :background (or background "rgba(255,255,255,0.95)")
                      :border-radius (or border-radius "8px")
                      :box-shadow (or shadow "0 2px 10px rgba(0,0,0,0.1)")}
                     style)}
      (when (map? props)
        (dissoc props :padding :background :border-radius :shadow :style)))
     children]))

(defn overlay
  "Overlay component for absolute positioning"
  [props & children]
  (let [{:keys [position top right bottom left z-index style] :as attrs} (if (map? props) props {})]
    [:div
     (merge
      {:style (merge {:position (or position "absolute")
                      :top (or top 0)
                      :right (or right 0)
                      :bottom (or bottom 0)
                      :left (or left 0)
                      :z-index (or z-index 1000)}
                     style)}
      (when (map? props)
        (dissoc props :position :top :right :bottom :left :z-index :style)))
     children]))
