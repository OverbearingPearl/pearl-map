(ns pearl-map.features.style-editor.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [pearl-map.services.map-engine :as map-engine]))

(def default-building-styles
  {:light {:fill-color "#f0f0f0"
           :fill-opacity 1.0
           :fill-outline-color "#cccccc"}
   :dark {:fill-color "#2d3748"
          :fill-opacity 1.0
          :fill-outline-color "#4a5568"}})

(defn get-current-zoom []
  (map-engine/get-current-zoom))



(defn get-layer-styles [layer-id current-style]
  (let [current-zoom (get-current-zoom)
        style-keys [:fill-color :fill-opacity :fill-outline-color]
        map-instance (map-engine/get-map-instance)]

    ;; For raster style, explicitly return unsupported state
    (if (= current-style "raster-style")
      {:fill-color :unsupported
       :fill-opacity :unsupported
       :fill-outline-color :unsupported}

      ;; For vector styles, proceed with normal logic
      (->> style-keys
           (map (fn [style-key]
                  (if (and map-instance (map-engine/layer-exists? layer-id))
                    (let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                      (if (some? current-value)
                        ;; Property exists - parse normally
                        (let [parsed-value (cond
                                             (= style-key :fill-opacity)
                                             (map-engine/parse-numeric-expression current-value current-zoom)

                                             (#{:fill-color :fill-outline-color} style-key)
                                             (map-engine/parse-color-expression current-value current-zoom)

                                             :else
                                             current-value)]
                          [style-key parsed-value])
                        ;; Property is nil - return nil to indicate "NOT SET"
                        [style-key nil]))
                    ;; Layer doesn't exist - return nil to indicate "NOT SET"
                    [style-key nil])))
           (into {})))))

(defn get-current-building-styles [target-layer current-style]
  (get-layer-styles target-layer current-style))

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

(defn update-building-style
  ([style-key value]
   (update-building-style style-key value nil))
  ([style-key value zoom]
   (when value
     (let [target-layer (get @re-frame.db/app-db :style-editor/target-layer "building")
           current-zoom (or zoom (get-current-zoom))
           processed-value (cond
                             (#{:fill-color :fill-outline-color} style-key)
                             ;; For colors, just pass the value directly
                             (if (string? value) value (str value))

                             (= style-key :fill-opacity)
                             ;; For opacity, parse to number
                             (let [num-value (if (string? value)
                                               (let [parsed (js/parseFloat value)]
                                                 (if (js/isNaN parsed) nil parsed))
                                               (if (number? value) value nil))]
                               num-value)

                             :else value)]
       (when (some? processed-value)
         (let [updated-value (map-engine/update-zoom-value-pair target-layer (name style-key) current-zoom processed-value)]
           (re-frame/dispatch [:style-editor/update-and-apply-style style-key updated-value])))))))

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
      (re-frame/dispatch [:style-editor/set-target-layer "building"])
      ;; Reset styles immediately to handle initial state
      (re-frame/dispatch [:style-editor/reset-styles-immediately]))
    :reagent-render
    (fn []
      (let [editing-style @(re-frame/subscribe [:style-editor/editing-style])
            target-layer @(re-frame/subscribe [:style-editor/target-layer])
            current-style @(re-frame/subscribe [:current-style])]
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

         ;; Show unsupported message for raster style
         (when (= current-style "raster-style")
           [:div {:style {:background "#fff3cd"
                          :border "1px solid #ffeaa7"
                          :padding "10px"
                          :border-radius "4px"
                          :margin-bottom "15px"}}
            [:p {:style {:margin "0" :color "#856404" :font-size "12px" :font-weight "bold"}}
             "Style editing is not supported in Raster mode."]
            [:p {:style {:margin "5px 0 0 0" :color "#856404" :font-size "11px"}}
             "Switch to Dark or Light vector styles to edit building styles."]])

         ;; Only show controls for vector styles
         (when (not= current-style "raster-style")
           (let [layer-exists? (and (map-engine/get-map-instance)
                                    (map-engine/layer-exists? target-layer))]
             [:div
              ;; Show warning if layer doesn't exist
              (when-not layer-exists?
                [:div {:style {:background "#f8d7da"
                               :border "1px solid #f5c6cb"
                               :padding "10px"
                               :border-radius "4px"
                               :margin-bottom "15px"}}
                 [:p {:style {:margin "0" :color "#721c24" :font-size "12px" :font-weight "bold"}}
                  (str "Layer '" target-layer "' does not exist in current style.")]
                 [:p {:style {:margin "5px 0 0 0" :color "#721c24" :font-size "11px"}}
                  "Style editing will not work for this layer."]])
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

              ;; Only show style controls if layer exists
              (when layer-exists?
                [:div
                 ;; Style controls
                 [:div {:style {:margin-bottom "10px"}}
                  [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Fill Color"]
                  (let [current-zoom (get-current-zoom)
                        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-color" current-zoom)]
                    (if (> (count zoom-pairs) 1)
                      ;; Multiple zoom levels
                      [:div {:style {:display "flex" :gap "5px" :flex-wrap "wrap"}}
                       (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
                         [:div {:key (str zoom "-" index) :style {:position "relative" :flex "1" :min-width "60px"}}
                          [:input {:type "color"
                                   :value (if (= value "transparent") "#f0f0f0" (or value "#f0f0f0"))
                                   :on-change #(let [new-color (-> % .-target .-value)]
                                                 (when new-color
                                                   (update-building-style :fill-color new-color zoom)))
                                   :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"
                                           :opacity (if (= value "transparent") 0.3 1.0)
                                           :background "transparent"}}]
                          [:div {:style {:font-size "10px" :text-align "center" :margin-top "2px" :color "#666"}}
                           (str "z" zoom)]
                          (when (= value "transparent")
                            [:div {:style {:position "absolute" :top "0" :left "0" :right "0" :height "30px"
                                           :display "flex" :align-items "center" :justify-content "center"
                                           :background "repeating-linear-gradient(45deg, #ccc, #ccc 2px, #eee 2px, #eee 4px)"
                                           :border-radius "4px" :color "#666" :font-weight "bold" :pointer-events "none"
                                           :font-size "8px" :line-height "1"}}
                             "TRANSPARENT"])])]
                      ;; Single zoom level
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
                        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-opacity" current-zoom)]
                    (if (> (count zoom-pairs) 1)
                      ;; Multiple zoom levels
                      [:div
                       (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
                         [:div {:key (str zoom "-" index) :style {:margin-bottom "10px"}}
                          [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom "5px"}}
                           [:span {:style {:font-size "11px" :color "#666"}} (str "z" zoom)]
                           [:span {:style {:font-size "11px" :color "#666"}}
                            (str (-> (or value 0) (* 100) js/Math.round) "%")]]
                          [:input {:type "range"
                                   :min "0" :max "1" :step "0.1"
                                   :value (or value 0)
                                   :on-change #(let [new-opacity (-> % .-target .-value js/parseFloat)]
                                                 (when (and (not (js/isNaN new-opacity)) (>= new-opacity 0))
                                                   (update-building-style :fill-opacity new-opacity zoom)))
                                   :style {:width "100%"}}]])]
                      ;; Single zoom level
                      [:div
                       [:input {:type "range"
                                :min "0" :max "1" :step "0.1"
                                :value (let [opacity (:fill-opacity editing-style)
                                             color-transparent? (= (:fill-color editing-style) "transparent")]
                                         (cond
                                           color-transparent? 0
                                           (number? opacity) opacity
                                           :else 1))
                                :on-change #(let [new-opacity (-> % .-target .-value js/parseFloat)]
                                              (when (and (not (js/isNaN new-opacity)) (>= new-opacity 0))
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
                  (let [outline-color (:fill-outline-color editing-style)]
                    [:div {:style {:position "relative"}}
                     [:input {:type "color"
                              :value (if (some? outline-color)
                                       (if (= outline-color "transparent") "#f0f0f0" outline-color)
                                       "#f0f0f0")
                              :on-change #(update-building-style :fill-outline-color (-> % .-target .-value))
                              :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"
                                      :opacity (if (or (= outline-color "transparent") (nil? outline-color)) 0.3 1.0)
                                      :background "transparent"}}]
                     (when (or (= outline-color "transparent") (nil? outline-color))
                       [:div {:style {:position "absolute" :top "0" :left "0" :right "0" :height "30px"
                                      :display "flex" :align-items "center" :justify-content "center"
                                      :background "repeating-linear-gradient(45deg, #ccc, #ccc 2px, #eee 2px, #eee 4px)"
                                      :border-radius "4px" :color "#666" :font-weight "bold" :pointer-events "none"
                                      :font-size "10px" :line-height "1"}}
                        (if (nil? outline-color) "NOT SET" "TRANSPARENT")])])]

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
                 ])

              ;; Status information
              [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
               [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
                (str "Current Layer: " target-layer)]
               [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
                "Only works with Dark or Light vector styles"]
               [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
                "Styles: " (pr-str (-> editing-style
                                       (select-keys [:fill-color :fill-opacity :fill-outline-color])
                                       (update :fill-color #(if (nil? %) "NOT SET" %))
                                       (update :fill-opacity #(if (nil? %) "NOT SET" %))
                                       (update :fill-outline-color #(if (nil? %) "NOT SET" %))))]]]))]))}))
