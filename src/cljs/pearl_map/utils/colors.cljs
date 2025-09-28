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

(defn parse-color-expression [color-value current-zoom]
  (when color-value
    (cond
      ;; Handle simple values
      (string? color-value)
      (if (.startsWith color-value "#")
        color-value
        (try
          (let [parsed (js/parseFloat color-value)]
            (if (js/isNaN parsed)
              (-> (color color-value)
                  (.hex)
                  (.toString)
                  (.toLowerCase))
              parsed))
          (catch js/Error e
            (-> (color color-value)
                (.hex)
                (.toString)
                (.toLowerCase)))))

      (number? color-value)
      color-value

      ;; Handle expressions with stops (for both colors and numbers)
      (and (object? color-value) (.-stops color-value))
      (let [stops-array (.-stops color-value)
            stops (js->clj stops-array)
            sorted-stops (sort-by first stops)]
        (if (empty? sorted-stops)
          (throw (js/Error. "Empty stops array"))
          (let [first-stop (first sorted-stops)
                last-stop (last sorted-stops)]
            (cond
              (<= current-zoom (first first-stop)) (second first-stop)
              (>= current-zoom (first last-stop)) (second last-stop)
              :else
              (loop [[[z1 v1] & rest-stops] sorted-stops]
                (if (empty? rest-stops)
                  (second first-stop)
                  (let [[z2 v2] (first rest-stops)]
                    (if (and (>= current-zoom z1) (<= current-zoom z2))
                      ;; Interpolate between stops if we're in the range
                      (let [base (or (.-base color-value) 1)
                            t (/ (- current-zoom z1) (- z2 z1))
                            ;; Apply exponential interpolation if base is not 1
                            t' (if (= base 1)
                                 t
                                 (/ (- (js/Math.pow base t) 1) (- base 1)))]
                        (cond
                          ;; Handle numeric values (like opacity)
                          (and (number? v1) (number? v2))
                          (+ v1 (* t' (- v2 v1)))

                          ;; Handle color values
                          (and (string? v1) (string? v2))
                          (let [color1 (color v1)
                                color2 (color v2)
                                mixed (.mix color1 color2 t')]
                            (.string mixed))

                          ;; Fallback to the lower stop's value
                          :else v1))
                      (recur rest-stops)))))))))

      ;; Handle interpolate expressions - treat them the same as stops expressions
      (and (object? color-value) (.-interpolate color-value))
      (let [stops-array (.-stops color-value)
            stops (js->clj stops-array)
            sorted-stops (sort-by first stops)]
        (if (empty? sorted-stops)
          (throw (js/Error. "Empty stops array in interpolate expression"))
          (let [first-stop (first sorted-stops)
                last-stop (last sorted-stops)]
            (cond
              (<= current-zoom (first first-stop)) (second first-stop)
              (>= current-zoom (first last-stop)) (second last-stop)
              :else
              (loop [[[z1 v1] & rest-stops] sorted-stops]
                (if (empty? rest-stops)
                  (second first-stop)
                  (let [[z2 v2] (first rest-stops)]
                    (if (and (>= current-zoom z1) (<= current-zoom z2))
                      ;; Interpolate between stops if we're in the range
                      (let [base (or (.-base color-value) 1)
                            t (/ (- current-zoom z1) (- z2 z1))
                            ;; Apply exponential interpolation if base is not 1
                            t' (if (= base 1)
                                 t
                                 (/ (- (js/Math.pow base t) 1) (- base 1)))]
                        (cond
                          ;; Handle numeric values (like opacity)
                          (and (number? v1) (number? v2))
                          (+ v1 (* t' (- v2 v1)))

                          ;; Handle color values
                          (and (string? v1) (string? v2))
                          (let [color1 (color v1)
                                color2 (color v2)
                                mixed (.mix color1 color2 t')]
                            (.string mixed))

                          ;; Fallback to the lower stop's value
                          :else v1))
                      (recur rest-stops)))))))))

      ;; Handle step expressions
      (and (object? color-value) (.-step color-value))
      (let [input (.-input color-value)
            stops (.-stops color-value)
            default (.-default color-value)]
        (let [value (or default (when stops (aget stops 1)))]
          (if (number? value)
            value
            (js/parseFloat value))))

      ;; Handle simple object values (like numbers wrapped in objects)
      (and (object? color-value) (.-valueOf color-value))
      (let [value (.valueOf color-value)]
        (if (number? value)
          value
          (js/parseFloat value)))

      ;; For other complex expressions, try to extract a reasonable value
      (object? color-value)
      (do
        (js/console.warn "Complex expression detected, attempting to extract value:" (js/JSON.stringify color-value))
        ;; Try various common properties
        (let [value (cond
                      (.-default color-value) (.-default color-value)
                      (.-value color-value) (.-value color-value)
                      :else 0.7)]
          (if (number? value)
            value
            (js/parseFloat value))))

      :else
      (do
        (js/console.warn "Unexpected color value type:" (type color-value) "value:" color-value)
        color-value))))
