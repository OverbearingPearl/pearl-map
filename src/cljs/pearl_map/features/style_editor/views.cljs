(ns pearl-map.features.style-editor.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [pearl-map.services.map-engine :as map-engine]))

(def default-building-styles
  {:light {:fill-color "#f0f0f0"
           :fill-opacity 0.7
           :fill-outline-color "#cccccc"}
   :dark {:fill-color "#2d3748"
          :fill-opacity 0.8
          :fill-outline-color "#4a5568"}})

(defn get-current-zoom []
  (map-engine/get-current-zoom))

(defn update-color-stop [stops zoom new-color]
  (let [updated-stops (mapv (fn [[stop-zoom stop-color]]
                              (if (= stop-zoom zoom)
                                [stop-zoom new-color]
                                [stop-zoom stop-color]))
                            stops)]
    {:stops updated-stops}))

(defn parse-color-stops [color-value current-zoom]
  (when color-value
    (cond
      ;; Handle stops expressions
      (and (map-engine/isExpression color-value) (.-stops color-value))
      (let [stops (.-stops color-value)]
        (mapv (fn [[zoom color]]
                {:zoom zoom
                 :color (map-engine/parse-color-expression color current-zoom)})
              stops))

      ;; Handle single color values
      :else
      [{:zoom current-zoom
        :color (map-engine/parse-color-expression color-value current-zoom)}])))

;; Add helper function to parse opacity stops
(defn parse-opacity-stops [opacity-value current-zoom]
  (when opacity-value
    (cond
      ;; Handle stops expressions
      (and (map-engine/isExpression opacity-value) (.-stops opacity-value))
      (let [stops (.-stops opacity-value)]
        (mapv (fn [[zoom opacity]]
                {:zoom zoom
                 :opacity (map-engine/parse-numeric-expression opacity current-zoom)})
              stops))

      ;; Handle single opacity values
      :else
      [{:zoom current-zoom
        :opacity (map-engine/parse-numeric-expression opacity-value current-zoom)}])))

;; Add helper function to update opacity stops
(defn update-opacity-stop [stops zoom new-opacity]
  (let [updated-stops (mapv (fn [[stop-zoom stop-opacity]]
                              (if (= stop-zoom zoom)
                                [stop-zoom new-opacity]
                                [stop-zoom stop-opacity]))
                            stops)]
    {:stops updated-stops}))

(defn get-layer-styles [layer-id]
  (let [current-zoom (get-current-zoom)
        style-keys [:fill-color :fill-opacity :fill-outline-color]]
    (->> style-keys
         (map (fn [style-key]
                (let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                  ;; Add debugging
                  (js/console.log "Style key:" (name style-key)
                                  "Raw value:" current-value
                                  "Type:" (type current-value)
                                  "Zoom:" current-zoom)
                  ;; Provide defaults when property is undefined
                  (let [parsed-value (cond
                                       (nil? current-value)
                                       (case style-key
                                         :fill-opacity 1.0  ; Default to fully opaque
                                         :fill-outline-color "#cccccc"  ; Default outline color
                                         nil)  ; No default for fill-color

                                       (= style-key :fill-opacity)
                                       (do
                                         (js/console.log "Parsing opacity:" current-value)
                                         (map-engine/parse-numeric-expression current-value current-zoom))

                                       (#{:fill-color :fill-outline-color} style-key)
                                       (do
                                         (js/console.log "Parsing color:" current-value)
                                         (map-engine/parse-color-expression current-value current-zoom))

                                       :else
                                       current-value)]
                    ;; Add more debugging
                    (js/console.log "Parsed value:" parsed-value "for key:" (name style-key))
                    (when (some? parsed-value)
                      [style-key parsed-value])))))
         (remove nil?)
         (into {}))))

(defn get-current-building-styles []
  (let [target-layer @(re-frame/subscribe [:style-editor/target-layer])]
    (get-layer-styles target-layer)))

(defn apply-layer-style [layer-id style]
  (try
    (let [validation-result (map-engine/validate-style style)]
      (if validation-result
        (doseq [[style-key style-value] style]
          ;; Allow nil values to be set (they might clear the property)
          ;; Apply values directly - map-engine/set-paint-property handles conversion
          (map-engine/set-paint-property layer-id (name style-key) style-value))
        (js/console.error "Style validation failed - not applying changes" style)))
    (catch js/Error e
      (js/console.error (str "Failed to apply style to layer " layer-id ":") e)
      (throw e))))

(defn apply-current-style [style]
  (let [target-layer (get @re-frame.db/app-db :style-editor/target-layer "building")]
    (apply-layer-style target-layer style)))

(defn update-building-style [style-key value]
  (when value
    (let [processed-value (cond
                            (#{:fill-color :fill-outline-color} style-key)
                            (if (string? value) value (str value))

                            (= style-key :fill-opacity)
                            (let [num-value (if (string? value)
                                              (let [parsed (js/parseFloat value)]
                                                (if (js/isNaN parsed) nil parsed))
                                              (if (number? value) value nil))]
                              num-value)

                            :else value)]
      (when (some? processed-value)
        (re-frame/dispatch [:style-editor/update-and-apply-style style-key processed-value])))))

(defn setup-map-listener []
  (let [map-inst (.-pearlMapInstance js/window)]
    (when map-inst
      (.off map-inst "load")
      (.off map-inst "styledata")

      (.on map-inst "load"
           (fn []
             (re-frame/dispatch [:style-editor/on-map-load])))

      (.on map-inst "styledata"
           (fn []
             (re-frame/dispatch [:style-editor/reset-styles-immediately]))))))

(defn building-style-editor []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (setup-map-listener)
      ;; Initialize target layer
      (re-frame/dispatch [:style-editor/set-target-layer "building"]))
    :reagent-render
    (fn []
      (let [editing-style @(re-frame/subscribe [:style-editor/editing-style])
            target-layer @(re-frame/subscribe [:style-editor/target-layer])]
        [:div {:style {:position "absolute"
                       :top "100px"
                       :right "20px"
                       :z-index 1000
                       :background "rgba(255,255,255,0.95)"
                       :padding "15px"
                       :border-radius "8px"
                       :font-family "Arial, sans-serif"
                       :width "320px"
                       :box-shadow "0 2px 10px rgba(0,0,0,0.1)"}}
         [:h3 {:style {:margin "0 0 15px 0" :color "#333"}} "Building Style Editor"]

         ;; Layer selector
         [:div {:style {:margin-bottom "15px"}}
          [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Target Layer"]
          [:select {:value target-layer
                    :on-change #(let [new-layer (-> % .-target .-value)]
                                  (re-frame/dispatch [:style-editor/set-target-layer new-layer])
                                  (re-frame/dispatch [:style-editor/reset-styles-immediately]))
                    :style {:width "100%" :padding "5px" :border "1px solid #ddd" :border-radius "4px"}}
           [:option {:value "building"} "Building"]
           [:option {:value "building-top"} "Building Top"]]]

         ;; Style controls
         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Fill Color"]
          (let [current-zoom (get-current-zoom)
                raw-fill-color (map-engine/get-paint-property target-layer "fill-color")
                color-stops (parse-color-stops raw-fill-color current-zoom)
                has-stops? (> (count color-stops) 1)]
            (if has-stops?
              ;; Display multiple color blocks for stops
              [:div {:style {:display "flex" :gap "5px" :flex-wrap "wrap"}}
               (for [{:keys [zoom color]} color-stops]
                 [:div {:key zoom :style {:position "relative" :flex "1" :min-width "60px"}}
                  [:input {:type "color"
                           :value (if (= color "transparent") "#f0f0f0" (or color "#f0f0f0"))
                           :on-change #(let [new-color (-> % .-target .-value)]
                                         (when new-color
                                           ;; Update the specific stop in the expression
                                           (let [updated-expression (update-color-stop (.-stops raw-fill-color) zoom new-color)]
                                             (re-frame/dispatch [:style-editor/update-and-apply-style :fill-color updated-expression]))))
                           :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"
                                   :opacity (if (= color "transparent") 0.3 1.0)
                                   :background "transparent"}}]
                  [:div {:style {:font-size "10px" :text-align "center" :margin-top "2px" :color "#666"}}
                   (str "z" zoom)]
                  (when (= color "transparent")
                    [:div {:style {:position "absolute" :top "0" :left "0" :right "0" :height "30px"
                                   :display "flex" :align-items "center" :justify-content "center"
                                   :background "repeating-linear-gradient(45deg, #ccc, #ccc 2px, #eee 2px, #eee 4px)"
                                   :border-radius "4px" :color "#666" :font-weight "bold" :pointer-events "none"
                                   :font-size "8px" :line-height "1"}}
                     "TRANSPARENT"])])]
              ;; Single color display (existing behavior)
              [:div {:style {:position "relative"}}
               [:input {:type "color"
                        :value (let [color-value (:fill-color editing-style)]
                                 (if (= color-value "transparent")
                                   "#f0f0f0"
                                   (or color-value "#f0f0f0")))
                        :on-change #(update-building-style :fill-color (-> % .-target .-value))
                        :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"
                                :opacity (if (= (:fill-color editing-style) "transparent") 0.3 1.0)
                                :background "transparent"}}]
               (when (= (:fill-color editing-style) "transparent")
                 [:div {:style {:position "absolute" :top "0" :left "0" :right "0" :height "30px"
                                :display "flex" :align-items "center" :justify-content "center"
                                :background "repeating-linear-gradient(45deg, #ccc, #ccc 2px, #eee 2px, #eee 4px)"
                                :border-radius "4px" :color "#666" :font-weight "bold" :pointer-events "none"
                                :font-size "10px" :line-height "1"}}
                  "TRANSPARENT"])]))]

         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Opacity"]
          (let [current-zoom (get-current-zoom)
                raw-fill-opacity (map-engine/get-paint-property target-layer "fill-opacity")
                opacity-stops (parse-opacity-stops raw-fill-opacity current-zoom)
                has-stops? (> (count opacity-stops) 1)]
            (if has-stops?
              ;; Display multiple opacity sliders for stops
              [:div
               (for [{:keys [zoom opacity]} opacity-stops]
                 [:div {:key zoom :style {:margin-bottom "10px"}}
                  [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom "5px"}}
                   [:span {:style {:font-size "11px" :color "#666"}} (str "z" zoom)]
                   [:span {:style {:font-size "11px" :color "#666"}}
                    (str (-> (or opacity 0) (* 100) js/Math.round) "%")]]
                  [:input {:type "range"
                           :min "0" :max "1" :step "0.1"
                           :value (or opacity 0)
                           :on-change #(let [new-opacity (-> % .-target .-value js/parseFloat)]
                                         (when (and (not (js/isNaN new-opacity)) (>= new-opacity 0))
                                           ;; Update the specific stop in the expression
                                           (let [updated-expression (update-opacity-stop (.-stops raw-fill-opacity) zoom new-opacity)]
                                             (re-frame/dispatch [:style-editor/update-and-apply-style :fill-opacity updated-expression]))))
                           :style {:width "100%"}}]])]
              ;; Single opacity slider (existing behavior)
              [:div
               [:input {:type "range"
                        :min "0" :max "1" :step "0.1"
                        :value (let [opacity (:fill-opacity editing-style)
                                     color-transparent? (= (:fill-color editing-style) "transparent")]
                                 (cond
                                   color-transparent? 0
                                   (number? opacity) opacity
                                   ;; Default to 1 if opacity is not set (common in vector styles)
                                   :else 1))
                        :on-change #(let [new-opacity (-> % .-target .-value js/parseFloat)]
                                      (when (and (not (js/isNaN new-opacity)) (>= new-opacity 0))
                                        ;; If we're increasing opacity from 0 and color is transparent,
                                        ;; we need to set a default color first
                                        (when (and (= (:fill-color editing-style) "transparent") (> new-opacity 0))
                                          (update-building-style :fill-color "#f0f0f0"))
                                        (update-building-style :fill-opacity new-opacity)))
                        :style {:width "100%"}}]
               [:span {:style {:font-size "12px" :color "#666"}}
                (str "Current: " (let [opacity (:fill-opacity editing-style)
                                       color-transparent? (= (:fill-color editing-style) "transparent")]
                                   (cond
                                     color-transparent? "0% (transparent)"
                                     (number? opacity) (-> opacity (* 100) js/Math.round (str "%"))
                                     :else "100% (default)")))]]))]

         [:div {:style {:margin-bottom "15px"}}
          [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Outline Color"]
          [:input {:type "color"
                   :value (or (:fill-outline-color editing-style) "#cccccc")
                   :on-change #(update-building-style :fill-outline-color (-> % .-target .-value))
                   :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"}}]]

         ;; Action buttons
         [:div {:style {:display "flex" :gap "10px" :margin-bottom "15px" :flex-wrap "wrap"}}
          [:button {:on-click #(re-frame/dispatch [:style-editor/set-and-apply-style (:light default-building-styles)])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#007bff" :color "white" :cursor "pointer" :flex "1"}} "Light"]
          [:button {:on-click #(re-frame/dispatch [:style-editor/set-and-apply-style (:dark default-building-styles)])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#343a40" :color "white" :cursor "pointer" :flex "1"}} "Dark"]
          [:button {:on-click #(re-frame/dispatch [:style-editor/reset-styles-immediately])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#28a745" :color "white" :cursor "pointer" :flex "1"}} "Reset"]]

         ;; Status information
         [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
          [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
           (str "Current Layer: " target-layer)]
          [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
           "Only works with Dark or Light vector styles"]
          [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
           "Styles: " (pr-str (select-keys editing-style [:fill-color :fill-opacity :fill-outline-color]))]]]))}))
