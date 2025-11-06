(ns pearl-map.components.map.controls
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.components.ui.buttons :as ui-buttons]
            [pearl-map.components.ui.layout :as ui-layout]))

(defn change-map-style [style-key]
  (let [style-url (get map-engine/style-urls style-key)]
    (re-frame/dispatch [:set-current-style-key style-key])
    (map-engine/change-map-style style-url)))

(defn style-controls []
  (let [current-style-url @(re-frame/subscribe [:current-style-url])
        show-other-components? @(re-frame/subscribe [:show-other-components?])
        map-instance @(re-frame/subscribe [:map-instance])]
    [ui-layout/card
     {:width "280px"}
     ;; Title and style controls in horizontal layout
     [:div {:key "header-section" :style {:display "flex" :justify-content "space-between" :align-items "flex-start" :margin-bottom "15px"}}
      ;; Title content on the left
      [:div {:key "title-section" :style {:flex "1" :margin-right "15px"}}
       [:h1 {:key "title" :style {:margin "0 0 8px 0" :font-size "1.4em" :color "#333" :line-height "1.2"}}
        "Pearl Map - Paris 3D"]
       [:p {:key "subtitle" :style {:margin "0 0 4px 0" :font-size "0.85em" :color "#666" :line-height "1.3"}}
        "Centered at Eiffel Tower"]
       [:p {:key "coordinates" :style {:margin "0" :font-size "0.75em" :color "#999" :line-height "1.2"}}
        "(2.2945°E, 48.8584°N)"]]

      ;; Style controls on the right
      [:div {:key "style-section" :style {:flex "0 0 auto"}}
       [:h3 {:key "style-title" :style {:margin "0 0 10px 0" :font-size "1.1em"}} "Map Style"]
       [ui-layout/flex-container {:key "button-row" :gap "5px" :wrap "wrap" :align "center"}
        [ui-buttons/primary-button {:key "basic-style" :on-click #(change-map-style :raster-style) :disabled? (nil? map-instance)} "Basic"]
        [ui-buttons/dark-button {:key "dark-style" :on-click #(change-map-style :dark-style) :disabled? (nil? map-instance)} "Dark"]
        [ui-buttons/light-button {:key "light-style" :on-click #(change-map-style :light-style) :disabled? (nil? map-instance)} "Light"]
        ;; Toggle button now in the same row as style buttons
        [ui-buttons/danger-button {:key "toggle-button"
                                   :on-click #(re-frame/dispatch [:toggle-other-components])
                                   :style {:margin-left "5px"}}
         (if show-other-components? "Hide" "Show")]]]]

     ;; Current style indicator below
     [ui-layout/flex-container {:key "current-style" :align "flex-start" :style {:margin-top "10px"}}
      [:span {:key "current-style-text" :style {:font-size "12px" :color "#666"}}
       "Current: " (str current-style-url)]]]))
