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

(re-frame/reg-event-db
 :style-editor/set-selected-category
 (fn [db [_ category]]
   (assoc db :style-editor/selected-category category)))

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
   (let [updated-db (assoc-in db [:style-editor/editing-style style-key] value)]
     (if (and (= style-key :visibility) (= value "visible"))
       {:db updated-db
        :dispatch [:style-editor/re-apply-all-styles]}
       {:db updated-db}))))

(re-frame/reg-event-fx
 :style-editor/re-apply-all-styles
 (fn [{:keys [db]} _]
   (let [target-layer (get-in db [:style-editor/target-layer])
         editing-style (get-in db [:style-editor/editing-style])]
     (doseq [[style-key style-value] editing-style]
       (when (and (some? style-value)
                  (contains? (set style-editor-views/paint-style-keys) style-key))
         (map-engine/set-paint-property target-layer (name style-key) style-value))))
   {}))

(re-frame/reg-event-fx
 :style-editor/apply-single-style
 (fn [{:keys [db]} [_ style-key value]]
   (try
     (let [target-layer (get db :style-editor/target-layer)
           prop-type (if (contains? (set style-editor-views/layout-style-keys) style-key) "layout" "paint")]
       (if (= prop-type "layout")
         (map-engine/set-layout-property target-layer (name style-key) value)
         (map-engine/set-paint-property target-layer (name style-key) value)))
     {:db db}
     (catch js/Error e
       (js/console.error "Failed to apply single style:" e)
       {:db db}))))

(re-frame/reg-event-fx
 :style-editor/reset-styles-immediately
 (fn [{:keys [db]} _]
   (let [current-style-key (:current-style-key db)
         map-obj (map-engine/get-map-instance)
         default-target-layer "extruded-building"]
     (if (or (not map-obj) (= current-style-key :raster-style))
       {:db (-> db
                (assoc :style-editor/target-layer nil)
                (assoc :style-editor/editing-style nil))}
       (let [current-styles (style-editor-views/get-layer-styles default-target-layer (get map-engine/style-urls current-style-key))]
         {:db (-> db
                  (assoc :style-editor/target-layer default-target-layer)
                  (assoc :style-editor/editing-style current-styles)
                  (assoc :style-editor/selected-category :buildings))})))))

(re-frame/reg-event-fx
 :style-editor/on-map-load
 (fn [{:keys [db]} _]
   (let [target-layer (get db :style-editor/target-layer)
         current-style-key (:current-style-key db)
         map-obj (map-engine/get-map-instance)
         default-target-layer "extruded-building"]
     (if (or (not map-obj) (= current-style-key :raster-style))
       {:db (-> db
                (assoc :style-editor/target-layer nil)
                (assoc :style-editor/editing-style nil))}
       (let [effective-target-layer (if (and target-layer (map-engine/layer-exists? target-layer))
                                      target-layer
                                      default-target-layer)
             current-styles (style-editor-views/get-layer-styles effective-target-layer (get map-engine/style-urls current-style-key))]
         {:db (-> db
                  (assoc :style-editor/target-layer effective-target-layer)
                  (assoc :style-editor/editing-style current-styles)
                  (assoc :style-editor/selected-category :buildings))})))))
