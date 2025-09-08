(ns pearl-map.editor
  (:require [reagent.core :as reagent]))

;; Default building style configurations
(def default-building-styles
  {:light {:fill-color "#f0f0f0"
           :fill-opacity 0.7
           :fill-outline-color "#cccccc"}
   :dark {:fill-color "#2d3748"
          :fill-opacity 0.8
          :fill-outline-color "#4a5568"}})

;; Current editing style state
(def current-editing-style (reagent/atom (:light default-building-styles)))

(defn apply-current-style []
  "Apply the current editing style to the map buildings"
  (let [map-inst (.-pearlMapInstance js/window)
        style @current-editing-style]
    (js/console.log "=== DEBUG: Starting apply-current-style ===")
    (js/console.log "Map instance:", map-inst)
    (js/console.log "Current style:", style)

    (when map-inst
      (js/console.log "Map style URL:", (.-styleURL map-inst))

      ;; Wait for the map to be fully loaded
      (if (.-loaded map-inst)
        (do
          (js/console.log "Map is loaded, applying style to building layers...")
          ;; Target specific building layers we found in the debug output
          (let [building-layer-ids ["building" "building-top"]]
            (doseq [layer-id building-layer-ids]
              (when (.getLayer map-inst layer-id)
                (js/console.log "Applying style to layer:" layer-id)
                (doseq [[style-key style-value] style]
                  (try
                    (.setPaintProperty map-inst layer-id (name style-key) style-value)
                    (js/console.log "Set" (name style-key) "to" style-value "for layer" layer-id)
                    (catch js/Error e
                      (js/console.warn "Could not set property" (name style-key) "for layer" layer-id ":" e))))))
            (js/console.log "Building styles applied successfully")))
        (js/console.warn "Map is not loaded yet. Please wait for the map to load completely.")))
    (js/console.log "=== DEBUG: Finished apply-current-style ===")))

(defn update-building-style [style-key value]
  "Update building style and apply to map"
  (swap! current-editing-style assoc style-key value)
  (apply-current-style))

;; Add a function to check if we should listen for map load events
(defn setup-map-listener []
  "Setup listener to automatically apply styles when map loads"
  (let [map-inst (.-pearlMapInstance js/window)]
    (when map-inst
      ;; Remove any existing listeners to avoid duplicates
      (.off map-inst "load")
      (.on map-inst "load"
           (fn []
             (js/console.log "Map loaded, checking if we should apply building styles...")
             (apply-current-style))))))

(defn building-style-editor []
  "Building style editor component"
  ;; Setup map listener when component mounts
  (reagent/create-class
   {:component-did-mount
    (fn []
      (js/setTimeout setup-map-listener 1000))  ;; Delay to ensure map is initialized
    :reagent-render
    (fn []
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
                 :value (:fill-color @current-editing-style)
                 :on-change #(update-building-style :fill-color (-> % .-target .-value))
                 :style {:width "100%" :height "30px"}}]]

       [:div {:style {:margin-bottom "10px"}}
        [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Opacity"]
        [:input {:type "range"
                 :min "0" :max "1" :step "0.1"
                 :value (:fill-opacity @current-editing-style)
                 :on-change #(update-building-style :fill-opacity (js/parseFloat (-> % .-target .-value)))
                 :style {:width "100%"}}]
        [:span {:style {:font-size "12px"}} (str "Opacity: " (:fill-opacity @current-editing-style))]]

       [:div {:style {:margin-bottom "15px"}}
        [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Outline Color"]
        [:input {:type "color"
                 :value (:fill-outline-color @current-editing-style)
                 :on-change #(update-building-style :fill-outline-color (-> % .-target .-value))
                 :style {:width "100%" :height "30px"}}]]

       [:div {:style {:display "flex" :gap "10px" :margin-bottom "15px"}}
        [:button {:on-click #(do
                               (reset! current-editing-style (:light default-building-styles))
                               (apply-current-style))
                  :style {:padding "8px 12px" :border "none" :border-radius "4px"
                          :background "#007bff" :color "white" :cursor "pointer"}} "Light Theme"]
        [:button {:on-click #(do
                               (reset! current-editing-style (:dark default-building-styles))
                               (apply-current-style))
                  :style {:padding "8px 12px" :border "none" :border-radius "4px"
                          :background "#343a40" :color "white" :cursor "pointer"}} "Dark Theme"]]

       [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
        [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
         "Buildings Status:"]
        [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
         "Only works with Dark or Light vector styles"]]
       [:div {:style {:margin-top "10px"}}
        [:button {:on-click #(let [map-inst (.-pearlMapInstance js/window)]
                               (when map-inst
                                 (let [style-obj (.getStyle map-inst)
                                       layers (.-layers style-obj)]
                                   (js/console.log "=== Available Layers ===")
                                   (doseq [layer layers]
                                     (js/console.log "ID:" (.-id layer)
                                                     "| Type:" (.-type layer)
                                                     "| Source:" (.-source layer))))))
                  :style {:padding "8px 12px" :border "none" :border-radius "4px"
                          :background "#6c757d" :color "white" :cursor "pointer" :width "100%"}}
         "Debug Layers"]]])}))
