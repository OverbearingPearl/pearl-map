(ns pearl-map.events
  (:require [re-frame.core :as re-frame]))

;; Map instance
(re-frame/reg-event-db
 :set-map-instance
 (fn [db [_ map-instance]]
   (assoc db :map-instance map-instance)))

;; Current style
(re-frame/reg-event-db
 :set-current-style
 (fn [db [_ style-url]]
   (assoc db :current-style style-url)))

;; Model loaded status
(re-frame/reg-event-db
 :set-model-loaded
 (fn [db [_ loaded?]]
   (assoc db :model-loaded loaded?)))

;; Loaded model
(re-frame/reg-event-db
 :set-loaded-model
 (fn [db [_ model]]
   (assoc db :loaded-model model)))

;; Editing style
(re-frame/reg-event-db
 :set-editing-style
 (fn [db [_ style]]
   (assoc db :editing-style style)))

(re-frame/reg-event-db
 :update-editing-style
 (fn [db [_ key value]]
   (assoc-in db [:editing-style key] value)))

;; Add custom layers management
(re-frame/reg-event-db
 :register-custom-layer
 (fn [db [_ layer-id layer-impl]]
   (update db :custom-layers assoc layer-id layer-impl)))

(re-frame/reg-event-db
 :unregister-custom-layer
 (fn [db [_ layer-id]]
   (update db :custom-layers dissoc layer-id)))

;; Initialize custom-layers in the database
(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]
   {:map-instance nil
    :current-style "raster-style"
    :model-loaded false
    :loaded-model nil
    :custom-layers {}  ; Add this line
    :editing-style {:fill-color "#f0f0f0"
                    :fill-opacity 0.7
                    :fill-outline-color "#cccccc"}}))
