(ns pearl-map.utils.colors
  (:require ["color" :as color]))

(defn hex-to-rgba [hex-str opacity]
  (when hex-str
    (if (.startsWith hex-str "rgba")
      hex-str
      (let [opacity-value (if (number? opacity)
                            opacity
                            (js/parseFloat opacity))
            color-obj (color hex-str)
            rgb-obj (.rgb color-obj)
            rgba-obj (.alpha rgb-obj opacity-value)]
        (.string rgba-obj)))))

(defn rgba-to-hex [rgba-value]
  (when rgba-value
    (cond
      (string? rgba-value)
      (if (.startsWith rgba-value "#")
        rgba-value
        (-> (color rgba-value)
            (.hex)
            (.toString)
            (.toLowerCase)))

      (object? rgba-value)
      (throw (js/Error. (str "Complex color expressions not supported in rgba-to-hex: " (js/JSON.stringify rgba-value))))

      :else
      (throw (js/Error. (str "Invalid color value type: " (type rgba-value)))))))

