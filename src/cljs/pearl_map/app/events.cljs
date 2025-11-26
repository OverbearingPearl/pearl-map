(ns pearl-map.app.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.app.db :as app-db]
            [pearl-map.config :as config]
            [pearl-map.features.lighting.events]
            [pearl-map.services.map-engine :as map-engine]
            [re-frame.db :as rf-db]
            ["maplibre-gl" :as maplibre]))

(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]
   app-db/default-db))

(re-frame/reg-event-db
 :set-map-instance
 (fn [db [_ map-instance]]
   (assoc db :map-instance map-instance)))

(re-frame/reg-event-db
 :set-map-loading?
 (fn [db [_ loading?]]
   (assoc db :map-loading? loading?)))

(re-frame/reg-event-fx
 :set-current-style-key
 (fn [{:keys [db]} [_ style-key]]
   (let [default-light-props (:map/light-properties app-db/default-db)
         default-building-style (:style-editor/editing-style app-db/default-db)
         new-db (-> db
                    (assoc :current-style-key style-key)
                    (assoc :map/light-properties default-light-props)
                    (assoc :style-editor/editing-style default-building-style)
                    (assoc :style-editor/target-layer nil))]
     {:db new-db})))

(re-frame/reg-event-db
 :register-custom-layer
 (fn [db [_ layer-id layer-impl]]
   (update db :custom-layers assoc layer-id layer-impl)))

(re-frame/reg-event-db
 :unregister-custom-layer
 (fn [db [_ layer-id]]
   (update db :custom-layers dissoc layer-id)))

(re-frame/reg-event-db
 :clear-custom-layers
 (fn [db _]
   (assoc db :custom-layers {})))

(re-frame/reg-event-db
 :toggle-other-components
 (fn [db _]
   (update db :show-other-components? not)))

(re-frame/reg-event-db
 :map/set-zoom
 (fn [db [_ zoom]]
   (assoc db :map/zoom zoom)))

(re-frame/reg-event-db
 :map/set-prewarming?
 (fn [db [_ prewarming?]]
   (assoc db :map/prewarming? prewarming?)))

(defonce debounce-timers (atom {}))

(re-frame/reg-fx
 :dispatch-debounce
 (fn [{:keys [key event delay]}]
   (when-let [timer-id (get @debounce-timers key)]
     (js/clearTimeout timer-id))
   (let [new-timer-id (js/setTimeout #(re-frame/dispatch event) (or delay 300))]
     (swap! debounce-timers assoc key new-timer-id))))

(defn init-map []
  (let [map-obj (map-engine/init-map)]
    (when map-obj
      (map-engine/on-map-load
       (fn [^maplibre/Map map-instance]
         (let [light-props (:map/light-properties @rf-db/app-db)
               prewarm-key "pearl-map-prewarmed"]
           (when light-props
             (.setLight map-instance (clj->js light-props)))
           (re-frame/dispatch [:buildings/add-layers])
           (when-not (.getItem js/localStorage prewarm-key)
             (re-frame/dispatch [:map/set-prewarming? true])
             (js/console.log "Prewarming map tiles for the first time...")
             (map-engine/prewarm-tiles
              config/eiffel-tower-coords
              (fn []
                (re-frame/dispatch [:map/set-prewarming? false])
                (.setItem js/localStorage prewarm-key "true")
                (js/console.log "Tile prewarming complete.")))))))
      (map-engine/on-map-error
       (fn [e]
         (js/console.error "Map loading error:" e))))))
