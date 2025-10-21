(ns pearl-map.features.map-view.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]))

(defn init-map []
  (let [map-obj (map-engine/init-map)]
    (when map-obj
      (map-engine/on-map-load
       (fn [map-instance]
         ;; Map loaded callback - 3D model loading should be handled separately
         (js/console.log "Map loaded successfully")))
      (map-engine/on-map-error
       (fn [e]
         (js/console.error "Map loading error:" e))))))

(defn change-map-style [style-url]
  (re-frame/dispatch [:set-current-style style-url])
  (map-engine/change-map-style style-url))
