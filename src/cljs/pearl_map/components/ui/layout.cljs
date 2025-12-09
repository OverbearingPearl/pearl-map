(ns pearl-map.components.ui.layout)

(defn flex-container
  [{:keys [direction align justify gap wrap style class] :as props} & children]
  (let [styles (cond-> {:display "flex"}
                 direction (assoc :flex-direction direction)
                 align     (assoc :align-items align)
                 justify   (assoc :justify-content justify)
                 gap       (assoc :gap gap)
                 wrap      (assoc :flex-wrap wrap)
                 style     (merge style))
        clean-props (dissoc props :direction :align :justify :gap :wrap :style :class)]
    (into [:div (assoc clean-props
                       :class (str "flex-container " (or class ""))
                       :style styles)]
          children)))

(defn grid-container
  [{:keys [columns gap style class] :as props} & children]
  (let [styles (cond-> {:display "grid"}
                 columns (assoc :grid-template-columns columns)
                 gap     (assoc :gap gap)
                 style   (merge style))
        clean-props (dissoc props :columns :gap :style :class)]
    (into [:div (assoc clean-props
                       :class (str "grid-container " (or class ""))
                       :style styles)]
          children)))

(defn card
  [{:keys [padding background border-radius shadow width style class] :as props} & children]
  (let [styles (cond-> {}
                 padding       (assoc :padding padding)
                 background    (assoc :background background)
                 border-radius (assoc :border-radius border-radius)
                 shadow        (assoc :box-shadow shadow)
                 width         (assoc :width width)
                 style         (merge style))
        clean-props (dissoc props :padding :background :border-radius :shadow :width :style :class)]
    (into [:div (assoc clean-props
                       :class (str "card " (or class ""))
                       :style styles)]
          children)))

(defn overlay
  [{:keys [position top right bottom left z-index style class] :as props} & children]
  (let [styles (cond-> {}
                 position (assoc :position position)
                 top      (assoc :top top)
                 right    (assoc :right right)
                 bottom   (assoc :bottom bottom)
                 left     (assoc :left left)
                 z-index  (assoc :z-index z-index)
                 style    (merge style))
        clean-props (dissoc props :position :top :right :bottom :left :z-index :style :class)]
    (into [:div (assoc clean-props
                       :class (str "overlay " (or class ""))
                       :style styles)]
          children)))
