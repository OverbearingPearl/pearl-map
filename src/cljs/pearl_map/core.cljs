(ns pearl-map.core
  (:require [reagent.core :as reagent]
            [reagent.dom.client :as rdomc]
            [re-frame.core :as re-frame]
            [pearl-map.app.events :as app-events]
            [pearl-map.app.subs :as app-subs]
            [pearl-map.features.map-view.views :refer [home-page]]
            [pearl-map.features.style-editor.events :as style-editor-events]
            [pearl-map.features.style-editor.subs :as style-editor-subs]))

(defonce react-root
  (delay
    (let [app-element (.getElementById js/document "app")]
      (when app-element
        (rdomc/create-root app-element)))))

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
