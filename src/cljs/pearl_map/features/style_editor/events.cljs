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
   (let [current-styles (style-editor-views/get-layer-styles layer-id)]
     {:db (-> db
              (assoc :style-editor/target-layer layer-id)
              (assoc :style-editor/editing-style current-styles))})))

(re-frame/reg-event-fx
 :style-editor/update-and-apply-style
 (fn [{:keys [db]} [_ style-key value]]
   (let [updated-style (assoc (:style-editor/editing-style db) style-key value)]
     {:db (assoc db :style-editor/editing-style updated-style)})))

(re-frame/reg-event-fx
 :style-editor/actually-apply-styles
 (fn [{:keys [db]} [_ style]]
   {:fx [[:dispatch [:style-editor/apply-styles style]]]}))

(re-frame/reg-event-fx
 :style-editor/manually-apply-current-style
 (fn [{:keys [db]} _]
   (let [current-style (:style-editor/editing-style db)]
     {:fx [[:dispatch [:style-editor/apply-styles current-style]]]})))

(re-frame/reg-event-fx
 :style-editor/apply-styles
 (fn [{:keys [db]} [_ style]]
   (try
     (js/console.log "Applying styles:" (clj->js style))
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

;; Add helper function to access get-layer-styles
(defn get-layer-styles [layer-id]
  (style-editor-views/get-layer-styles layer-id))

(re-frame/reg-event-fx
 :style-editor/immediate-refresh-styles
 (fn [{:keys [db]} _]
   (let [target-layer (get db :style-editor/target-layer "building")
         current-styles (get-layer-styles target-layer)]
     {:db (assoc db :style-editor/editing-style current-styles)})))

(re-frame/reg-event-fx
 :style-editor/refresh-styles-after-idle
 (fn [{:keys [db]} _]
   (let [target-layer (get db :style-editor/target-layer "building")
         current-styles (get-layer-styles target-layer)]
     ;; Only update the editing style, don't re-apply it (to avoid loops)
     {:db (assoc db :style-editor/editing-style current-styles)})))

(re-frame/reg-event-fx
 :style-editor/on-map-load
 (fn [{:keys [db]} _]
   (let [current-styles (style-editor-views/get-current-building-styles)]
     {:db (assoc db :style-editor/editing-style current-styles)})))
