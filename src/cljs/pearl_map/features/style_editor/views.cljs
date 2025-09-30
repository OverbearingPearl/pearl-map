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
        layer-ids ["building" "building-top"]
        style-keys [:fill-color :fill-opacity :fill-outline-color]]
    (->> layer-ids
         (mapcat (fn [layer-id]
                   (->> style-keys
                        (map (fn [style-key]
                               (when-let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                                 [style-key (map-engine/parse-color-expression current-value current-zoom)])))
                        (remove nil?))))
         (into {}))))

(defn apply-current-style [style]
  (try
    (let [validation-result (map-engine/validate-style style)
          current-zoom (get-current-zoom)]
      (if validation-result
        (doseq [layer-id ["building" "building-top"]]
          (try
            (doseq [[style-key style-value] style]
              (let [final-value (map-engine/parse-color-expression style-value current-zoom)]
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
                            (#{:fill-color :fill-outline-color} style-key) value
                            (= style-key :fill-opacity) (js/parseFloat value)
                            :else value)]
      (re-frame/dispatch [:style-editor/update-and-apply-style style-key processed-value]))))

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
                   :value (:fill-opacity editing-style)
                   :on-change #(update-building-style :fill-opacity (-> % .-target .-value))
                   :style {:width "100%"}}]
          [:span {:style {:font-size "12px"}} (str "Opacity: " (:fill-opacity editing-style))]]
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
          [:button {:on-click #(re-frame/dispatch [:style-editor/load-and-apply-current-styles])
                    :style {:padding "8px 12px" :border "none" :border-radius "4px"
                            :background "#28a745" :color "white" :cursor "pointer"}} "Refresh Styles"]]
         [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
          [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
           "Buildings Status:"]
          [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
           "Only works with Dark or Light vector styles"]
          [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
           "Current: " (pr-str (select-keys editing-style [:fill-color :fill-opacity :fill-outline-color]))]]]))}))
