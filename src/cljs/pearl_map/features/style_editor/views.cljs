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

(defn get-current-building-styles []
  (let [current-zoom (get-current-zoom)
        ;; Prefer building-top layer for expressions, fall back to building layer
        layer-ids ["building-top" "building"]
        numeric-properties #{:fill-opacity}
        color-properties #{:fill-color :fill-outline-color}
        style-keys [:fill-color :fill-opacity :fill-outline-color]]

    (js/console.log "DEBUG get-current-building-styles:"
                    "current-zoom:" current-zoom)

    (let [all-styles (->> layer-ids
                          (mapcat (fn [layer-id]
                                    (->> style-keys
                                         (map (fn [style-key]
                                                (when-let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                                                  (js/console.log "DEBUG processing property:"
                                                                  "layer:" layer-id
                                                                  "key:" style-key
                                                                  "value:" current-value
                                                                  "isExpression:" (map-engine/isExpression current-value))
                                                  (let [parsed-value (cond
                                                                       ;; Numeric expressions
                                                                       (and (numeric-properties style-key)
                                                                            (map-engine/isExpression current-value))
                                                                       (map-engine/parse-numeric-expression current-value current-zoom)

                                                                       ;; Color expressions - handle gracefully
                                                                       (and (color-properties style-key)
                                                                            (map-engine/isExpression current-value))
                                                                       (or (map-engine/parse-color-expression current-value current-zoom)
                                                                           ;; Fallback for color expression failures
                                                                           (do
                                                                             (js/console.warn "Color expression evaluation failed, using default")
                                                                             (case style-key
                                                                               :fill-color "#f0f0f0"
                                                                               :fill-outline-color "#cccccc"
                                                                               nil)))

                                                                       ;; Simple numeric values
                                                                       (numeric-properties style-key)
                                                                       (if (number? current-value)
                                                                         current-value
                                                                         (map-engine/parse-numeric-expression current-value current-zoom))

                                                                       ;; Simple color values
                                                                       (color-properties style-key)
                                                                       (if (string? current-value)
                                                                         current-value
                                                                         (map-engine/parse-color-expression current-value current-zoom))

                                                                       :else
                                                                       current-value)]
                                                    (when (some? parsed-value)
                                                      [style-key parsed-value])))))
                                         (remove nil?))))
                          (into {}))]
      ;; Use reasonable defaults only for missing properties
      (merge {:fill-color "#f0f0f0"
              :fill-opacity 0.7
              :fill-outline-color "#cccccc"}
             all-styles))))

(defn apply-current-style [style]
  (try
    (let [validation-result (map-engine/validate-style style)
          current-zoom (get-current-zoom)]
      (if validation-result
        ;; Only apply styles to layers that don't have expressions
        ;; This preserves zoom-dependent behavior
        (doseq [layer-id ["building" "building-top"]]
          (try
            (doseq [[style-key style-value] style]
              (let [current-raw-value (map-engine/get-paint-property layer-id (name style-key))
                    ;; CRITICAL: Only apply styles to layers without expressions
                    ;; This prevents breaking the zoom-dependent opacity system
                    final-value (if (map-engine/isExpression current-raw-value)
                                  ;; Layer has expression - preserve it, don't override
                                  current-raw-value
                                  ;; Layer has no expression - apply the new style
                                  style-value)]
                (when (and (some? final-value)
                           (not= final-value current-raw-value))
                  (map-engine/set-paint-property layer-id (name style-key) final-value))))
            (catch js/Error e
              (js/console.warn (str "Could not apply style to layer " layer-id ":") e))))
        (js/console.error "Style validation failed - not applying changes")))
    (catch js/Error e
      (js/console.error "Failed to apply building style:" e)
      (throw e))))

(defn update-building-style [style-key value]
  (when value
    (let [processed-value (cond
                            (#{:fill-color :fill-outline-color} style-key)
                            (if (string? value) value (str value))

                            (= style-key :fill-opacity)
                            (if (string? value)
                              (let [parsed (js/parseFloat value)]
                                (if (js/isNaN parsed)
                                  (do
                                    (js/console.warn "Invalid opacity value:" value)
                                    nil)
                                  parsed))
                              (if (number? value)
                                value
                                (do
                                  (js/console.warn "Unexpected opacity value type:" value)
                                  nil)))

                            :else value)]
      (when (some? processed-value)
        (re-frame/dispatch [:style-editor/update-and-apply-style style-key processed-value])))))

(defn force-refresh-styles []
  (let [current-zoom (get-current-zoom)]
    (js/console.log "FORCE REFRESH - Current zoom:" current-zoom)

    ;; Simply get the current styles without forcing zoom changes
    ;; This preserves expression objects
    (let [styles (get-current-building-styles)]
      (js/console.log "DEBUG refreshed styles:" styles)
      ;; Don't re-apply styles that would break expressions
      ;; Just update the UI state
      (re-frame/dispatch [:style-editor/set-editing-style styles]))))

(defn setup-map-listener []
  (let [map-inst (.-pearlMapInstance js/window)]
    (when map-inst
      (.off map-inst "load")
      (.on map-inst "load"
           (fn []
             (re-frame/dispatch [:style-editor/on-map-load]))))))

(defn building-style-editor []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (setup-map-listener))
    :reagent-render
    (fn []
      (let [editing-style @(re-frame/subscribe [:style-editor/editing-style])]
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
                   :value (let [opacity (:fill-opacity editing-style)]
                            (if (and opacity (not (js/isNaN opacity)))
                              opacity
                              0.7))  ;; Only provide default for display, not processing
                   :on-change #(update-building-style :fill-opacity (-> % .-target .-value))
                   :style {:width "100%"}}]
          [:span {:style {:font-size "12px"}}
           (str "Opacity: " (let [opacity (:fill-opacity editing-style)]
                              (if (and opacity (not (js/isNaN opacity)))
                                opacity
                                "Unknown")))]]
         [:div {:style {:margin-bottom "15px"}}
          [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Outline Color"]
          [:input {:type "color"
                   :value (or (:fill-outline-color editing-style) "#cccccc")
                   :on-change #(update-building-style :fill-outline-color (-> % .-target .-value))
                   :style {:width "100%" :height "30px"}}]]
         [:div {:style {:display "flex" :gap "10px" :margin-bottom "15px" :flex-wrap "wrap"}}
          [:button {:on-click #(re-frame/dispatch [:style-editor/set-and-apply-style (:light default-building-styles)])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#007bff" :color "white" :cursor "pointer"}} "Light Theme"]
          [:button {:on-click #(re-frame/dispatch [:style-editor/set-and-apply-style (:dark default-building-styles)])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#343a40" :color "white" :cursor "pointer"}} "Dark Theme"]
          [:button {:on-click force-refresh-styles
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#28a745" :color "white" :cursor "pointer"}} "Refresh Styles"]
          [:button {:on-click #(re-frame/dispatch [:style-editor/manually-apply-current-style])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#dc3545" :color "white" :cursor "pointer"}} "Apply Changes"]]
         [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
          [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
           "Buildings Status:"]
          [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
           "Only works with Dark or Light vector styles"]
          [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
           "Current: " (pr-str (select-keys editing-style [:fill-color :fill-opacity :fill-outline-color]))]]]))}))
