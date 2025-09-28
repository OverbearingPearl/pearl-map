(ns pearl-map.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.utils.colors :as colors]
            [pearl-map.services.map-engine :as map-engine]))

(re-frame/reg-event-db
 :set-map-instance
 (fn [db [_ map-instance]]
   (assoc db :map-instance map-instance)))

(re-frame/reg-event-db
 :set-current-style
 (fn [db [_ style-url]]
   (assoc db :current-style style-url)))

(re-frame/reg-event-db
 :set-model-loaded
 (fn [db [_ loaded?]]
   (assoc db :model-loaded loaded?)))

(re-frame/reg-event-db
 :set-loaded-model
 (fn [db [_ model]]
   (assoc db :loaded-model model)))

(re-frame/reg-event-db
 :style-editor/set-editing-style
 (fn [db [_ style]]
   (assoc db :style-editor/editing-style style)))

(re-frame/reg-event-db
 :style-editor/update-editing-style
 (fn [db [_ key value]]
   (assoc-in db [:style-editor/editing-style key] value)))

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

(re-frame/reg-event-fx
 :style-editor/on-map-load
 (fn [{:keys [db]} _]
   {:db db
    :fx [[:dispatch [:style-editor/load-current-styles]]]}))

(re-frame/reg-event-fx
 :style-editor/load-current-styles
 (fn [{:keys [db]} _]
   (let [current-zoom (map-engine/get-current-zoom)
         layer-ids ["building" "building-top"]
         style-keys [:fill-color :fill-opacity :fill-outline-color]
         current-styles (->> layer-ids
                           (mapcat (fn [layer-id]
                                     (->> style-keys
                                          (map (fn [style-key]
                                                 (when-let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                                                   [style-key (colors/parse-color-expression current-value current-zoom)])))
                                          (remove nil?))))
                           (into {}))]
     {:db (reduce (fn [current-db [style-key style-value]]
                    (assoc-in current-db [:style-editor/editing-style style-key] style-value))
                  db
                  current-styles)
      :fx [[:dispatch [:style-editor/apply-current-styles current-styles]]]})))

(re-frame/reg-event-fx
 :style-editor/apply-current-styles
 (fn [_ [_ styles]]
   {:fx [[:dispatch-later {:ms 100 :dispatch [:style-editor/actually-apply-styles styles]}]]}))

(re-frame/reg-event-fx
 :style-editor/update-and-apply-style
 (fn [{:keys [db]} [_ style-key value]]
   (let [updated-style (assoc (:style-editor/editing-style db) style-key value)]
     {:db (assoc db :style-editor/editing-style updated-style)
      :fx [[:dispatch-later {:ms 100 :dispatch [:style-editor/actually-apply-styles updated-style]}]]})))

(re-frame/reg-event-fx
 :style-editor/set-and-apply-style
 (fn [{:keys [db]} [_ style]]
   {:db (assoc db :style-editor/editing-style style)
    :fx [[:dispatch-later {:ms 100 :dispatch [:style-editor/actually-apply-styles style]}]]}))

(re-frame/reg-event-fx
 :style-editor/load-and-apply-current-styles
 (fn [{:keys [db]} _]
   (let [current-zoom (map-engine/get-current-zoom)
         layer-ids ["building" "building-top"]
         style-keys [:fill-color :fill-opacity :fill-outline-color]
         current-styles (->> layer-ids
                           (mapcat (fn [layer-id]
                                     (->> style-keys
                                          (map (fn [style-key]
                                                 (when-let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                                                   [style-key (colors/parse-color-expression current-value current-zoom)])))
                                          (remove nil?))))
                           (into {}))]
     {:db (assoc db :style-editor/editing-style current-styles)
      :fx [[:dispatch-later {:ms 100 :dispatch [:style-editor/actually-apply-styles current-styles]}]]})))

(re-frame/reg-event-fx
 :style-editor/actually-apply-styles
 (fn [_ [_ styles]]
   (pearl-map.features.style-editor.views/apply-current-style styles)
   {}))

(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]
   {:map-instance nil
    :current-style "raster-style"
    :model-loaded false
    :loaded-model nil
    :custom-layers {}
    :style-editor/editing-style {:fill-color "#f0f0f0"
                                 :fill-opacity 0.7
                                 :fill-outline-color "#cccccc"}}))
