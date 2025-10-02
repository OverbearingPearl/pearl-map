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
        style-keys [:fill-color :fill-opacity :fill-outline-color]
        ^js map-obj (map-engine/get-map-instance)]

    (if (and map-obj (.getLayer map-obj layer-id))
      (let [layer-styles (->> style-keys
                              (map (fn [style-key]
                                     (when-let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                                       (let [parsed-value (cond
                                                            (= style-key :fill-opacity)
                                                            (map-engine/parse-numeric-expression current-value current-zoom)

                                                            (#{:fill-color :fill-outline-color} style-key)
                                                            (let [color-value (map-engine/parse-color-expression current-value current-zoom)]
                                                              (if (= color-value "transparent")
                                                                "transparent"
                                                                color-value))

                                                            :else
                                                            current-value)]
                                         (when (some? parsed-value)
                                           [style-key parsed-value])))))
                              (remove nil?)
                              (into {}))]

        (merge {:fill-color "#f0f0f0"
                :fill-opacity 0.7
                :fill-outline-color "#cccccc"}
               layer-styles))
      ;; Return default styles if layer doesn't exist
      {:fill-color "#f0f0f0"
       :fill-opacity 0.7
       :fill-outline-color "#cccccc"})))

(defn get-current-building-styles []
  (let [target-layer @(re-frame/subscribe [:style-editor/target-layer])]
    (get-layer-styles target-layer)))

(defn apply-layer-style [layer-id style]
  (try
    (when-let [^js map-obj (map-engine/get-map-instance)]
      (when (.getLayer map-obj layer-id)
        (doseq [[style-key style-value] style]
          ;; Skip :gradient key as it's UI metadata, not a MapLibre paint property
          (when (and (some? style-value) (not= style-key :gradient))
            ;; Apply both literal values and expression objects
            (map-engine/set-paint-property layer-id (name style-key) style-value)))))
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

(defn- gradient-controls [editing-style update-building-style]
  (let [gradient-data (:gradient editing-style {:type "uniform"
                                                :uniform-color "#f0f0f0"
                                                :uniform-opacity 0.7
                                                :stops [{:zoom 0 :color "#f0f0f0" :opacity 0.3}
                                                        {:zoom 22 :color "#f0f0f0" :opacity 0.9}]})]
    [:div
     ;; Mode selector
     [:div {:style {:margin-bottom "15px"}}
      [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Style Mode"]
      [:div {:style {:display "flex" :gap "10px"}}
       [:label {:style {:display "flex" :align-items "center" :gap "5px"}}
        [:input {:type "radio"
                 :name "style-mode"
                 :checked (= (:type gradient-data) "uniform")
                 :on-change #(update-building-style :gradient
                                                    (assoc gradient-data :type "uniform"))}]
        "Uniform"]
       [:label {:style {:display "flex" :align-items "center" :gap "5px"}}
        [:input {:type "radio"
                 :name "style-mode"
                 :checked (= (:type gradient-data) "gradient")
                 :on-change #(update-building-style :gradient
                                                    (assoc gradient-data :type "gradient"))}]
        "Gradient"]]]

     (if (= (:type gradient-data) "uniform")
       ;; Uniform style controls
       [:div
        [:div {:style {:margin-bottom "10px"}}
         [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Fill Color"]
         [:input {:type "color"
                  :value (:uniform-color gradient-data "#f0f0f0")
                  :on-change #(update-building-style :gradient
                                                     (assoc gradient-data :uniform-color (-> % .-target .-value)))
                  :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"}}]]
        [:div {:style {:margin-bottom "15px"}}
         [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Opacity"]
         [:div
          [:input {:type "range"
                   :min "0" :max "1" :step "0.1"
                   :value (:uniform-opacity gradient-data 0.7)
                   :on-change #(update-building-style :gradient
                                                      (assoc gradient-data :uniform-opacity (-> % .-target .-value js/parseFloat)))
                   :style {:width "100%"}}]
          [:span {:style {:font-size "12px" :color "#666"}}
           (str "Current: " (-> (or (:uniform-opacity gradient-data) 0.7) (* 100) js/Math.round (str "%")))]]]]

       ;; Gradient style controls
       [:div
        (for [i (range 2)]
          (let [stop (get-in gradient-data [:stops i])]
            [:div {:key i :style {:margin-bottom "15px" :padding "10px" :border "1px solid #eee" :border-radius "4px"}}
             [:div {:style {:font-weight "bold" :margin-bottom "5px"}}
              (if (= i 0) "Low Zoom (≤)" "High Zoom (≥)")]
             [:div {:style {:margin-bottom "5px"}}
              [:label {:style {:display "block" :margin-bottom "2px" :font-size "12px"}} "Zoom Level"]
              [:input {:type "number"
                       :min "0" :max "22" :step "1"
                       :value (:zoom stop)
                       :on-change #(update-building-style :gradient
                                                          (assoc-in gradient-data [:stops i :zoom] (-> % .-target .-value js/parseFloat)))
                       :style {:width "100%" :padding "3px" :border "1px solid #ddd" :border-radius "2px"}}]]
             [:div {:style {:margin-bottom "5px"}}
              [:label {:style {:display "block" :margin-bottom "2px" :font-size "12px"}} "Color"]
              [:input {:type "color"
                       :value (:color stop "#f0f0f0")
                       :on-change #(update-building-style :gradient
                                                          (assoc-in gradient-data [:stops i :color] (-> % .-target .-value)))
                       :style {:width "100%" :height "25px" :border "1px solid #ddd" :border-radius "2px"}}]]
             [:div
              [:label {:style {:display "block" :margin-bottom "2px" :font-size "12px"}} "Opacity"]
              [:input {:type "range"
                       :min "0" :max "1" :step "0.1"
                       :value (:opacity stop 0.7)
                       :on-change #(update-building-style :gradient
                                                          (assoc-in gradient-data [:stops i :opacity] (-> % .-target .-value js/parseFloat)))
                       :style {:width "100%"}}]]]))])]))

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
                       :width "350px"
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

         ;; Gradient controls
         [gradient-controls editing-style update-building-style]

         ;; Outline color (always uniform)
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
           "Mode: " (get-in editing-style [:gradient :type] "uniform")]]]))}))
