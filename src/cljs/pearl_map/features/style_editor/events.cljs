(ns pearl-map.features.style-editor.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.features.style-editor.views :as style-editor-views]))

;; Layer state management
(re-frame/reg-event-db
 :style-editor/set-target-layer
 (fn [db [_ layer-id]]
   (assoc db :style-editor/target-layer layer-id)))

(re-frame/reg-event-fx
 :style-editor/set-selected-category
 (fn [{:keys [db]} [_ category]]
   (let [default-layer (get-in style-editor-views/layer-categories [category :default-layer])
         current-style-key (:current-style-key db)
         current-style-url (get map-engine/style-urls current-style-key)
         current-styles (style-editor-views/get-layer-styles default-layer current-style-url)]
     {:db (-> db
              (assoc :style-editor/selected-category category)
              (assoc :style-editor/target-layer default-layer)
              (assoc :style-editor/editing-style current-styles))})))

(defn- init-to-default-db [db]
  (let [category :buildings
        default-layer (get-in style-editor-views/layer-categories [category :default-layer])
        current-style-key (:current-style-key db)
        current-style-url (get map-engine/style-urls current-style-key)
        current-styles (style-editor-views/get-layer-styles default-layer current-style-url)]
    (-> db
        (assoc :style-editor/selected-category category)
        (assoc :style-editor/target-layer default-layer)
        (assoc :style-editor/editing-style current-styles))))

(re-frame/reg-event-fx
 :style-editor/initialize
 (fn [{:keys [db]} _]
   (if (get db :style-editor/selected-category)
     {:db db}
     {:db (init-to-default-db db)})))

(re-frame/reg-event-fx
 :style-editor/switch-target-layer
 (fn [{:keys [db]} [_ layer-id]]
   (let [current-style-key (:current-style-key db)
         current-style-url (get map-engine/style-urls current-style-key)
         current-styles (style-editor-views/get-layer-styles layer-id current-style-url)
         zoom-level (style-editor-views/get-zoom-for-layer layer-id)]
     {:db (-> db
              (assoc :style-editor/target-layer layer-id)
              (assoc :style-editor/editing-style current-styles))
      :dispatch [:style-editor/fly-to-feature layer-id zoom-level]})))

(re-frame/reg-event-fx
 :style-editor/fly-to-feature
 (fn [_ [_ layer-id zoom-level]]
   (map-engine/focus-on-layer layer-id zoom-level)
   {}))

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
 :style-editor/reset-styles-immediately
 (fn [{:keys [db]} _]
   (let [current-style-key (:current-style-key db)
         map-obj (map-engine/get-map-instance)
         target-layer (:style-editor/target-layer db)]
     (if (or (not map-obj) (= current-style-key :raster-style) (not target-layer))
       ;; Do nothing if raster style or no layer is selected
       {:db db}
       (let [current-styles (style-editor-views/get-layer-styles target-layer (get map-engine/style-urls current-style-key))]
         {:db (assoc db :style-editor/editing-style current-styles)})))))

(re-frame/reg-event-fx
 :style-editor/reset-to-defaults
 (fn [{:keys [db]} _]
   (let [current-style-key (:current-style-key db)]
     (if (= current-style-key :raster-style)
       ;; When entering raster mode, clear the editor state.
       {:db (-> db
                (assoc :style-editor/target-layer nil)
                (assoc :style-editor/editing-style nil)
                (assoc :style-editor/selected-category nil))}
       ;; When entering a vector style, always re-initialize to default.
       {:db (init-to-default-db db)}))))
