(ns pearl-map.features.style-editor.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.features.style-editor.views :as style-editor-views]))

;; Layer state management
(re-frame/reg-event-db
 :style-editor/set-target-layer
 (fn [db [_ layer-id]]
   (assoc db :style-editor/target-layer layer-id)))

(re-frame/reg-event-db
 :style-editor/set-editing-style
 (fn [db [_ style]]
   (assoc db :style-editor/editing-style style)))

(re-frame/reg-event-db
 :style-editor/update-editing-style
 (fn [db [_ key value]]
   (assoc-in db [:style-editor/editing-style key] value)))

(re-frame/reg-event-fx
 :style-editor/switch-target-layer
 (fn [{:keys [db]} [_ layer-id]]
   (let [current-style (:current-style db)
         current-styles (style-editor-views/get-layer-styles layer-id current-style)]
     {:db (-> db
              (assoc :style-editor/target-layer layer-id)
              (assoc :style-editor/editing-style current-styles))})))

(re-frame/reg-event-fx
 :style-editor/update-and-apply-style
 (fn [{:keys [db]} [_ style-key value]]
   (let [updated-style (assoc (:style-editor/editing-style db) style-key value)]
     {:db (assoc db :style-editor/editing-style updated-style)
      :fx [[:dispatch [:style-editor/apply-single-style style-key value]]]})))

(re-frame/reg-event-fx
 :style-editor/actually-apply-styles
 (fn [{:keys [db]} [_ style]]
   {:fx [[:dispatch [:style-editor/apply-styles style]]]}))


(re-frame/reg-event-fx
 :style-editor/apply-single-style
 (fn [{:keys [db]} [_ style-key value]]
   (try
     (let [target-layer (get db :style-editor/target-layer)
           paint-property (case style-key
                            :fill-extrusion-color "fill-extrusion-color"
                            :fill-color "fill-color"
                            :fill-outline-color "fill-outline-color"
                            :fill-opacity "fill-opacity"
                            (name style-key))]
       (map-engine/set-paint-property target-layer paint-property value))
     {:db db}
     (catch js/Error e
       (js/console.error "Failed to apply single style:" e)
       {:db db}))))

(re-frame/reg-event-fx
 :style-editor/apply-styles
 (fn [{:keys [db]} [_ style]]
   (try
     (style-editor-views/apply-current-style style)
     {:db db}
     (catch js/Error e
       (js/console.error "Failed to apply styles:" e)
       {:db db}))))

(re-frame/reg-event-fx
 :style-editor/set-and-apply-style
 (fn [{:keys [db]} [_ style]]
   {:db (assoc db :style-editor/editing-style style)
    :fx [[:dispatch [:style-editor/apply-styles style]]]}))

(defn get-layer-styles [layer-id current-style]
  (style-editor-views/get-layer-styles layer-id current-style))

(re-frame/reg-event-fx
 :style-editor/reset-styles-immediately
 (fn [{:keys [db]} _]
   (let [target-layer (get db :style-editor/target-layer)
         current-style (:current-style db)
         current-styles (get-layer-styles target-layer current-style)]
     {:db (assoc db :style-editor/editing-style current-styles)})))

(re-frame/reg-event-fx
 :style-editor/on-map-load
 (fn [{:keys [db]} _]
   (let [target-layer (get db :style-editor/target-layer)
         current-style (:current-style db)
         current-styles (style-editor-views/get-current-building-styles target-layer current-style)]
     {:db (assoc db :style-editor/editing-style current-styles)})))
