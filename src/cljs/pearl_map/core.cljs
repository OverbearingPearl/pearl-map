(ns pearl-map.core
  (:require [reagent.core :as reagent]
            [reagent.dom.client :as rdomc]
            [re-frame.core :as re-frame]
            [pearl-map.events :as events]
            [pearl-map.subs :as subs]
            [pearl-map.editor :refer [building-style-editor]]
            [pearl-map.services.model-loader :as model-loader]
            [pearl-map.services.map-engine :as map-engine]))

(defonce react-root
  (delay
    (let [app-element (.getElementById js/document "app")]
      (when app-element
        (rdomc/create-root app-element)))))
(def style-urls map-engine/style-urls)

(defn map-container []
  [:div {:id "map-container"
         :style {:width "100%"
                 :height "100vh"
                 :position "absolute"
                 :top 0
                 :left 0}}])

(defn init-map []
  (let [map-obj (map-engine/init-map)]
    (when map-obj
      (map-engine/on-map-load
       (fn [map-instance]
         (model-loader/load-gltf-model
          "/models/eiffel_tower/scene.gltf"
          (fn [gltf-model]
            (re-frame/dispatch [:set-model-loaded true])
            (re-frame/dispatch [:set-loaded-model gltf-model])
            (set! (.-pearlMapModel js/window) gltf-model)))))
      (map-engine/on-map-error
       (fn [e]
         (js/console.error "Map loading error:" e))))))

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

(defn debug-info []
  (let [map-instance @(re-frame/subscribe [:map-instance])
        model-loaded @(re-frame/subscribe [:model-loaded])]
    [:div {:style {:position "absolute"
                   :bottom "20px"
                   :left "20px"
                   :z-index 1000
                   :background "rgba(255,255,255,0.9)"
                   :padding "10px"
                   :border-radius "5px"
                   :font-family "Arial, sans-serif"
                   :font-size "12px"}}
     [:div "Map Instance: " (if map-instance "Loaded" "Not Loaded")]
     [:div "Container: " (if (.getElementById js/document "map-container") "Exists" "Missing")]
     [:div "3D Model: " (if model-loaded "Loaded" "Not Loaded")]]))

(defn home-page []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (re-frame/dispatch-sync [:initialize-db])
      (init-map))
    :reagent-render
    (fn []
      [:div
       [:div {:style {:position "absolute"
                      :top "20px"
                      :left "20px"
                      :zIndex 1000
                      :background "rgba(255,255,255,0.9)"
                      :padding "10px"
                      :borderRadius "5px"
                      :fontFamily "Arial, sans-serif"}}
        [:h1 {:style {:margin 0 :fontSize "1.5em" :color "#333"}} "Pearl Map - Paris 3D"]
        [:p {:style {:margin "5px 0 0 0" :fontSize "0.9em" :color "#666"}}
         "Centered at Eiffel Tower (2.2945°E, 48.8584°N)"]
        [:p {:style {:margin "2px 0 0 0" :fontSize "0.8em" :color "#999"}}
         "Using MapLibre demo vector service"]]
       [style-controls]
       [building-style-editor]
       [map-container]
       [debug-info]])}))

(defn mount-root []
  (let [root @react-root]
    (when root
      (rdomc/render root [home-page]))))

(defn ^:dev/after-load reload []
  (when-let [root @react-root]
    (rdomc/render root [home-page])))

(defn init []
  (re-frame/clear-subscription-cache!)
  (mount-root))
