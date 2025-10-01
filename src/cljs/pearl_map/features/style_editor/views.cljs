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

(defn get-layer-styles [layer-id]
  (let [current-zoom (get-current-zoom)
        style-keys [:fill-color :fill-opacity :fill-outline-color]]

    (let [layer-styles (->> style-keys
                            (map (fn [style-key]
                                   (when-let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                                     (let [parsed-value (cond
                                                          (= style-key :fill-opacity)
                                                          (map-engine/parse-numeric-expression current-value current-zoom)

                                                          (#{:fill-color :fill-outline-color} style-key)
                                                          (map-engine/parse-color-expression current-value current-zoom)

                                                          :else
                                                          current-value)]
                                       (when (some? parsed-value)
                                         [style-key parsed-value])))))
                            (remove nil?)
                            (into {}))]

      (merge {:fill-color "#f0f0f0"
              :fill-opacity 0.7
              :fill-outline-color "#cccccc"}
             layer-styles))))

(defn get-current-building-styles []
  (let [target-layer @(re-frame/subscribe [:style-editor/target-layer])]
    (get-layer-styles target-layer)))

(defn apply-layer-style [layer-id style]
  (try
    (let [validation-result (map-engine/validate-style style)]
      (if validation-result
        (doseq [[style-key style-value] style]
          (when (some? style-value)
            ;; For building-top opacity, don't modify the expression - it should remain zoom-dependent
            ;; Only apply literal values to other properties and layers
            (if (and (= layer-id "building-top") (= style-key :fill-opacity))
              ;; Do nothing - preserve the original zoom-dependent expression
              nil
              ;; For other properties, set as literal values
              (map-engine/set-paint-property layer-id (name style-key) style-value))))
        (js/console.error "Style validation failed - not applying changes")))
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

(defn force-refresh-styles []
  ;; Use a small delay to ensure the map has fully updated
  (js/setTimeout
   #(re-frame/dispatch [:style-editor/load-and-apply-current-styles])
   100))

(defn setup-map-listener []
  (let [map-inst (.-pearlMapInstance js/window)]
    (when map-inst
      ;; Remove existing listeners
      (.off map-inst "load")
      (.off map-inst "zoom")
      (.off map-inst "idle")

      ;; Add new listeners
      (.on map-inst "load"
           (fn []
             (re-frame/dispatch [:style-editor/on-map-load])))

      ;; Refresh styles when zoom changes to show current effective values
      (.on map-inst "zoom"
           (fn []
             (let [target-layer @(re-frame/subscribe [:style-editor/target-layer])]
               (when (= target-layer "building-top")
                 ;; Use idle event to ensure map has finished rendering
                 (.once map-inst "idle" force-refresh-styles))))))))

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
                                  ;; Refresh styles when switching layers
                                  (re-frame/dispatch [:style-editor/load-and-apply-current-styles]))
                    :style {:width "100%" :padding "5px" :border "1px solid #ddd" :border-radius "4px"}}
           [:option {:value "building"} "Building"]
           [:option {:value "building-top"} "Building Top"]]]

         ;; Style controls
         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Fill Color"]
          [:input {:type "color"
                   :value (or (:fill-color editing-style) "#f0f0f0")
                   :on-change #(update-building-style :fill-color (-> % .-target .-value))
                   :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"}}]]

         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Opacity"]
          [:div
           [:input {:type "range"
                    :min "0" :max "1" :step "0.1"
                    :value (let [opacity (:fill-opacity editing-style)]
                             (if (and opacity (not (js/isNaN opacity)))
                               opacity
                               0.7))
                    :on-change #(update-building-style :fill-opacity (-> % .-target .-value))
                    :style {:width "100%"}
                    :disabled (= target-layer "building-top")}]
           [:span {:style {:font-size "12px" :color "#666"}}
            (str "Current: " (let [opacity (:fill-opacity editing-style)]
                               (if (and opacity (not (js/isNaN opacity)))
                                 (-> opacity (* 100) js/Math.round (str "%"))
                                 "Unknown")))
            (when (= target-layer "building-top")
              " (zoom-dependent)")]]]

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
          [:button {:on-click force-refresh-styles
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#28a745" :color "white" :cursor "pointer" :flex "1"}} "Refresh"]
          [:button {:on-click #(re-frame/dispatch [:style-editor/manually-apply-current-style])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#dc3545" :color "white" :cursor "pointer" :flex "1"}} "Apply"]]

         ;; Status information
         [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
          [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
           (str "Current Layer: " target-layer)]
          [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
           "Only works with Dark or Light vector styles"]
          [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
           "Styles: " (pr-str (select-keys editing-style [:fill-color :fill-opacity :fill-outline-color]))]]]))}))
