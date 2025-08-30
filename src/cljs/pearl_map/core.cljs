(ns pearl-map.core
  (:require [reagent.dom :as rdom]))

;; Define a simple component
(defn home-page []
  [:div
   [:h1 "Welcome to Pearl Map"]
   [:p "This is a Reagent-based application"]])

(defn mount-root []
  (js/console.log "Mounting root...")
  ;; Render the component to the DOM element with id "app"
  (rdom/render [home-page] (.getElementById js/document "app")))

(defn init []
  (js/console.log "Initializing pearl-map...")
  (mount-root))
