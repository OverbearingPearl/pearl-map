(ns pearl-map.utils.colors
  (:require ["color" :as color]
            [clojure.string :as str]))

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

(defn- component-to-hex [c]
  (let [hex (.toString (js/Math.round c) 16)]
    (if (= (count hex) 1) (str "0" hex) hex)))

(defn rgba-to-hex [rgba-value]
  (when rgba-value
    (cond
      (string? rgba-value)
      (if (.startsWith rgba-value "#")
        rgba-value
        ;; Try to parse string like "rgb(x,y,z)" or "rgba(x,y,z,a)"
        (if-let [parsed-rgba (if (.startsWith rgba-value "rgb")
                               (let [[_ r g b a] (re-matches #"rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)" rgba-value)]
                                 (when r [(js/parseInt r) (js/parseInt g) (js/parseInt b) (if a (js/parseFloat a) 1)]))
                               nil)]
          (str "#"
               (component-to-hex (nth parsed-rgba 0))
               (component-to-hex (nth parsed-rgba 1))
               (component-to-hex (nth parsed-rgba 2)))
          ;; Fallback for other string formats, let color lib handle it, then convert to hex
          (try
            (-> (color rgba-value)
                (.hex)
                (.toString)
                (.toLowerCase))
            (catch js/Error e
              nil)))) ; Return nil if color lib fails to parse
      (vector? rgba-value) ;; Handle [r g b a] vector
      (if (= (count rgba-value) 4)
        (str "#"
             (component-to-hex (nth rgba-value 0))
             (component-to-hex (nth rgba-value 1))
             (component-to-hex (nth rgba-value 2)))
        (throw (js/Error. (str "Invalid RGBA vector length: " (count rgba-value)))))

      (object? rgba-value)
      (throw (js/Error. (str "Complex color expressions not supported in rgba-to-hex: " (js/JSON.stringify rgba-value))))

      :else
      (throw (js/Error. (str "Invalid color value type: " (type rgba-value)))))))

(defn parse-rgba-string [s]
  (when (string? s)
    (let [match (re-matches #"rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)" s)]
      (when match
        (let [[_ r g b a] match]
          [(js/parseInt r) (js/parseInt g) (js/parseInt b) (if a (js/parseFloat a) 1)])))))
