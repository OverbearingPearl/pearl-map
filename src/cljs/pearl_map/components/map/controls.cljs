(ns pearl-map.components.map.controls
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.components.ui.buttons :as ui-buttons]
            [pearl-map.components.ui.layout :as ui-layout]))

(def style-urls map-engine/style-urls)

(defn change-map-style [style-url]
  (re-frame/dispatch [:set-current-style style-url])
  (map-engine/change-map-style style-url))

(defn add-example-custom-layer []
  (let [custom-layer (map-engine/create-example-custom-layer)]
    (map-engine/add-custom-layer "example-custom-layer" custom-layer nil)))

(defn style-controls []
  (let [current-style @(re-frame/subscribe [:current-style])]
    [ui-layout/card
     {:width "250px"}
     [:h3 {:key "title" :style {:margin "0 0 10px 0"}} "Map Style"]
     [ui-layout/flex-container {:key "button-row" :gap "5px" :wrap "wrap"}
      [ui-buttons/primary-button {:key "basic-style" :on-click #(change-map-style (:basic style-urls))} "Basic"]
      [ui-buttons/dark-button {:key "dark-style" :on-click #(change-map-style (:dark style-urls))} "Dark"]
      [ui-buttons/light-button {:key "light-style" :on-click #(change-map-style (:light style-urls))} "Light"]
      [ui-buttons/success-button {:key "custom-layer" :on-click #(add-example-custom-layer)} "Custom"]]
     [ui-layout/flex-container {:key "current-style" :align "flex-start" :style {:margin-top "10px"}}
      [:span {:key "current-style-text" :style {:font-size "12px" :color "#666"}}
       "Current: " (str current-style)]]]))
