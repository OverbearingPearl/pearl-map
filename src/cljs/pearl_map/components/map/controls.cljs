(ns pearl-map.components.map.controls
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]))

(def style-urls map-engine/style-urls)

(defn change-map-style [style-url]
  (re-frame/dispatch [:set-current-style style-url])
  (map-engine/change-map-style style-url))

(defn add-example-custom-layer []
  (let [custom-layer (map-engine/create-example-custom-layer)]
    (map-engine/add-custom-layer "example-custom-layer" custom-layer nil)))

(defn style-controls []
  (let [current-style @(re-frame/subscribe [:current-style])]
    [:div {:style {:position "absolute"
                   :top "20px"
                   :right "20px"
                   :z-index 1000
                   :background "rgba(255,255,255,0.9)"
                   :padding "10px"
                   :border-radius "5px"
                   :font-family "Arial, sans-serif"}}
     [:h3 {:style {:margin "0 0 10px 0"}} "Map Style"]
     [:button {:on-click #(change-map-style (:basic style-urls))
               :style {:margin "5px" :padding "8px 12px" :border "none"
                       :border-radius "3px" :background "#007bff" :color "white"
                       :cursor "pointer"}} "Basic Style"]
     [:button {:on-click #(change-map-style (:dark style-urls))
               :style {:margin "5px" :padding "8px 12px" :border "none"
                       :border-radius "3px" :background "#343a40" :color "white"
                       :cursor "pointer"}} "Dark Style"]
     [:button {:on-click #(change-map-style (:light style-urls))
               :style {:margin "5px" :padding "8px 12px" :border "none"
                       :border-radius "3px" :background "#f8f9fa" :color "black"
                       :cursor "pointer"}} "Light Style"]
     [:button {:on-click #(add-example-custom-layer)
               :style {:margin "5px" :padding "8px 12px" :border "none"
                       :border-radius "3px" :background "#28a745" :color "white"
                       :cursor "pointer"}} "Add Custom Layer"]
     [:div {:style {:margin-top "10px" :font-size "12px" :color "#666"}}
      "Current: " (str current-style)]]))
