(ns pearl-map.editor
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-frame.db :refer [app-db]]
            [clojure.string :as str]
            [pearl-map.services.map-engine :as map-engine]))

(def default-building-styles
  {:light {:fill-color "#f0f0f0"
           :fill-opacity 0.7
           :fill-outline-color "#cccccc"}
   :dark {:fill-color "#2d3748"
          :fill-opacity 0.8
          :fill-outline-color "#4a5568"}})

(defn validate-style [style]
  (map-engine/validate-style style))

(defn get-current-zoom []
  (map-engine/get-current-zoom))

(defn get-opacity-value [opacity]
  (cond
    ;; 1. Simple number
    (number? opacity) opacity

    ;; 2. Stops format: {"stops": [[zoom1, value1], [zoom2, value2]]}
    (and (object? opacity) (.-stops opacity))
    (let [current-zoom (get-current-zoom)
          stops (.-stops opacity)
          sorted-stops (sort-by first (js->clj stops))]
      (if (empty? sorted-stops)
        (throw (js/Error. "Empty stops array"))
        (let [first-stop (first sorted-stops)
              last-stop (last sorted-stops)]
          (cond
            (<= current-zoom (first first-stop)) (second first-stop)
            (>= current-zoom (first last-stop)) (second last-stop)
            :else (loop [[[z1 v1] & rest-stops] sorted-stops]
                    (when (and rest-stops (first (first rest-stops)))
                      (let [[z2 v2] (first rest-stops)]
                        (if (and (>= current-zoom z1) (< current-zoom z2))
                          (+ v1 (* (- current-zoom z1) (/ (- v2 v1) (- z2 z1))))
                          (recur rest-stops)))))))))

    ;; 3. Expression format: ["interpolate", "linear", "zoom", z1, v1, z2, v2]
    (and (object? opacity) (.-expression opacity))
    (let [current-zoom (get-current-zoom)
          expr (.-expression opacity)]
      (if (and (array? expr)
               (= (aget expr 0) "interpolate")
               (= (aget expr 1) "linear")
               (= (aget expr 2) "zoom"))
        (let [stops (drop 3 (array-seq expr))
              stop-pairs (partition 2 stops)]
          (if (empty? stop-pairs)
            (throw (js/Error. "Empty expression stops"))
            (let [first-pair (first stop-pairs)
                  last-pair (last stop-pairs)]
              (cond
                (<= current-zoom (first first-pair)) (second first-pair)
                (>= current-zoom (first last-pair)) (second last-pair)
                :else (loop [[[z1 v1] [z2 v2] & more] stop-pairs]
                        (if (and z2 (>= current-zoom z1) (< current-zoom z2))
                          (+ v1 (* (- current-zoom z1) (/ (- v2 v1) (- z2 z1))))
                          (recur (cons [z2 v2] more))))))))
        (throw (js/Error. (str "Unsupported expression format: " (js/JSON.stringify expr))))))
    (object? opacity) (throw (js/Error. (str "Unsupported opacity object format: " (js/JSON.stringify opacity))))
    :else (throw (js/Error. (str "Invalid opacity value type: " (type opacity))))))

(defn hex-to-rgba [hex-str opacity]
  (when hex-str
    (if (str/starts-with? hex-str "rgba")
      hex-str
      (let [opacity-value (get-opacity-value opacity)]
        (map-engine/hex-to-rgba hex-str opacity-value)))))

(defn get-current-building-styles []
  (let [styles (atom {})]
    (doseq [layer-id ["building" "building-top"]]
      (doseq [style-key [:fill-color :fill-opacity :fill-outline-color]]
        (try
          (let [current-value (map-engine/get-paint-property layer-id (name style-key))]
            (when current-value
              (let [processed-value (cond
                                      (#{:fill-color :fill-outline-color} style-key)
                                      (map-engine/rgba-to-hex current-value)
                                      (= style-key :fill-opacity)
                                      (get-opacity-value current-value)
                                      :else current-value)]
                (swap! styles assoc style-key processed-value))))
          (catch js/Error e
            (js/console.warn (str "Could not get property " style-key " for layer " layer-id ":") e)))))
    @styles))

(defn apply-current-style [style]
  (try
    (let [validation-result (map-engine/validate-style style)]
      (if validation-result
        (doseq [layer-id ["building" "building-top"]]
          (try
            (doseq [[style-key style-value] style]
              (let [final-value (cond
                                  (#{:fill-color :fill-outline-color} style-key)
                                  (map-engine/hex-to-rgba style-value (:fill-opacity style))
                                  (= style-key :fill-opacity)
                                  style-value
                                  :else style-value)]
                (map-engine/set-paint-property layer-id (name style-key) final-value)))
            (catch js/Error e
              (js/console.warn (str "Could not apply style to layer " layer-id ":") e))))
        (js/console.error "Style validation failed - not applying changes")))
    (catch js/Error e
      (js/console.error "Failed to apply building style:" e)
      (throw e))))

(defn update-building-style [style-key value]
  (when (and value (not (str/blank? value)))
    (let [processed-value (cond
                            (#{:fill-color :fill-outline-color} style-key)
                            (if (string? value)
                              (map-engine/rgba-to-hex value)
                              (throw (js/Error. (str "Color value must be string, got: " (type value)))))
                            (= style-key :fill-opacity)
                            (if (number? value)
                              value
                              (js/parseFloat value))
                            :else value)]
      (re-frame/dispatch [:update-editing-style style-key processed-value])
      (let [updated-style (assoc @re-frame.db/app-db :editing-style
                                 (assoc (:editing-style @re-frame.db/app-db) style-key processed-value))]
        (apply-current-style (:editing-style updated-style))))))

(defn setup-map-listener []
  (let [map-inst (.-pearlMapInstance js/window)]
    (when map-inst
      (.off map-inst "load")
      (.on map-inst "load"
           (fn []
             (when-let [current-styles (get-current-building-styles)]
               (doseq [[style-key style-value] current-styles]
                 (re-frame/dispatch [:update-editing-style style-key style-value]))
               (apply-current-style current-styles)))))))

(defn building-style-editor []
  (let [mounted (reagent/atom false)]
    (reagent/create-class
     {:component-did-mount
      (fn []
        (reset! mounted true)
        (setup-map-listener))
      :component-will-unmount
      (fn []
        (reset! mounted false))
      :reagent-render
      (fn []
        (let [editing-style @(re-frame/subscribe [:editing-style])]
          [:div {:style {:position "absolute"
                         :top "100px"
                         :right "20px"
                         :z-index 1000
                         :background "rgba(255,255,255,0.95)"
                         :padding "15px"
                         :border-radius "8px"
                         :font-family "Arial, sans-serif"
                         :width "300px"
                         :box-shadow "0 2px 10px rgba(0,0,0,0.1)"}}
           [:h3 {:style {:margin "0 0 15px 0" :color "#333"}} "Building Style Editor"]
           [:div {:style {:margin-bottom "10px"}}
            [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Fill Color"]
            [:input {:type "color"
                     :value (or (:fill-color editing-style) "#f0f0f0")
                     :on-change #(update-building-style :fill-color (-> % .-target .-value))
                     :style {:width "100%" :height "30px"}}]]
           [:div {:style {:margin-bottom "10px"}}
            [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Opacity"]
            [:input {:type "range"
                     :min "0" :max "1" :step "0.1"
                     :value (:fill-opacity editing-style)
                     :on-change #(update-building-style :fill-opacity (js/parseFloat (-> % .-target .-value)))
                     :style {:width "100%"}}]
            [:span {:style {:font-size "12px"}} (str "Opacity: " (:fill-opacity editing-style))]]
           [:div {:style {:margin-bottom "15px"}}
            [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Outline Color"]
            [:input {:type "color"
                     :value (or (:fill-outline-color editing-style) "#cccccc")
                     :on-change #(update-building-style :fill-outline-color (-> % .-target .-value))
                     :style {:width "100%" :height "30px"}}]]
           [:div {:style {:display "flex" :gap "10px" :margin-bottom "15px" :flex-wrap "wrap"}}
            [:button {:on-click #(do
                                   (re-frame/dispatch [:set-editing-style (:light default-building-styles)])
                                   (apply-current-style (:light default-building-styles)))
                      :style {:padding "8px 12px" :border "none" :border-radius "4px"
                              :background "#007bff" :color "white" :cursor "pointer"}} "Light Theme"]
            [:button {:on-click #(do
                                   (re-frame/dispatch [:set-editing-style (:dark default-building-styles)])
                                   (apply-current-style (:dark default-building-styles)))
                      :style {:padding "8px 12px" :border "none" :border-radius "4px"
                              :background "#343a40" :color "white" :cursor "pointer"}} "Dark Theme"]
            [:button {:on-click #(when-let [current-styles (get-current-building-styles)]
                                   (doseq [[style-key style-value] current-styles]
                                     (re-frame/dispatch [:update-editing-style style-key style-value]))
                                   (apply-current-style current-styles))
                      :style {:padding "8px 12px" :border "none" :border-radius "4px"
                              :background "#28a745" :color "white" :cursor "pointer"}} "Refresh Styles"]]
           [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
            [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
             "Buildings Status:"]
            [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
             "Only works with Dark or Light vector styles"]
            [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
             "Current: " (pr-str (select-keys editing-style [:fill-color :fill-opacity :fill-outline-color]))]]]))})))
