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
     {:width "280px"}
     ;; Title content moved here
     [:div {:key "title-section" :style {:margin-bottom "15px" :border-bottom "1px solid #eee" :padding-bottom "10px"}}
      [:h1 {:key "title" :style {:margin "0 0 8px 0" :font-size "1.4em" :color "#333" :line-height "1.2"}}
       "Pearl Map - Paris 3D"]
      [:p {:key "subtitle" :style {:margin "0 0 4px 0" :font-size "0.85em" :color "#666" :line-height "1.3"}}
       "Centered at Eiffel Tower"]
      [:p {:key "coordinates" :style {:margin "0" :font-size "0.75em" :color "#999" :line-height "1.2"}}
       "(2.2945°E, 48.8584°N)"]]

     ;; Original style controls
     [:h3 {:key "style-title" :style {:margin "0 0 10px 0" :font-size "1.1em"}} "Map Style"]
     [ui-layout/flex-container {:key "button-row" :gap "5px" :wrap "wrap"}
      [ui-buttons/primary-button {:key "basic-style" :on-click #(change-map-style (:basic style-urls))} "Basic"]
      [ui-buttons/dark-button {:key "dark-style" :on-click #(change-map-style (:dark style-urls))} "Dark"]
      [ui-buttons/light-button {:key "light-style" :on-click #(change-map-style (:light style-urls))} "Light"]]
     [ui-layout/flex-container {:key "current-style" :align "flex-start" :style {:margin-top "10px"}}
      [:span {:key "current-style-text" :style {:font-size "12px" :color "#666"}}
       "Current: " (str current-style)]]]))
