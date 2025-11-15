(ns pearl-map.components.map.controls
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.components.ui.buttons :as ui-buttons]
            [pearl-map.components.ui.layout :as ui-layout]))

(defn change-map-style [style-key]
  (let [style-url (get map-engine/style-urls style-key)]
    (map-engine/change-map-style style-url)))

(defn style-controls []
  (let [current-style-key @(re-frame/subscribe [:current-style-key])
        current-style-url (get map-engine/style-urls current-style-key)
        show-other-components? @(re-frame/subscribe [:show-other-components?])
        map-instance @(re-frame/subscribe [:map-instance])]
    [ui-layout/card
     {:class "style-controls-card"}
     ;; Title and style controls in horizontal layout
     [:div {:key "header-section" :class "style-controls-header"}
      ;; Title content on the left
      [:div {:key "title-section" :class "style-controls-title-section"}
       [:h1 {:key "title" :class "style-controls-title"}
        "Pearl Map - Paris 3D"]
       [:p {:key "subtitle" :class "style-controls-subtitle"}
        "Centered at Eiffel Tower"]
       [:p {:key "coordinates" :class "style-controls-coords"}
        "(2.2945°E, 48.8584°N)"]]

      ;; Style controls on the right
      [:div {:key "style-section" :class "style-controls-style-section"}
       [:h3 {:key "style-title" :class "style-controls-style-title"} "Map Style"]
       [ui-layout/flex-container {:key "button-row" :gap "5px" :wrap "wrap" :align "center"}
        [ui-buttons/primary-button {:key "basic-style" :on-click #(change-map-style :raster-style) :disabled? (nil? map-instance)} "Basic"]
        [ui-buttons/dark-button {:key "dark-style" :on-click #(change-map-style :dark-style) :disabled? (nil? map-instance)} "Dark"]
        [ui-buttons/light-button {:key "light-style" :on-click #(change-map-style :light-style) :disabled? (nil? map-instance)} "Light"]
        ;; Toggle button now in the same row as style buttons
        [ui-buttons/danger-button {:key "toggle-button"
                                   :on-click #(re-frame/dispatch [:toggle-other-components])
                                   :class "style-controls-toggle-button"}
         (if show-other-components? "Hide" "Show")]]]]

     ;; Current style indicator below
     [ui-layout/flex-container {:key "current-style" :class "style-controls-current-style"}
      [:span {:key "current-style-text" :class "style-controls-current-style-text"}
       "Current: " (str current-style-url)]]]))
