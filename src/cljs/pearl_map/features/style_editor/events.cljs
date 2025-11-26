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
         current-zoom (:map/zoom db)
         current-styles (style-editor-views/get-layer-styles default-layer current-style-url current-zoom)]
     {:db (-> db
              (assoc :style-editor/selected-category category)
              (assoc :style-editor/target-layer default-layer)
              (assoc :style-editor/editing-style current-styles))})))

(defn- init-to-default-db [db]
  (let [category :buildings
        default-layer (get-in style-editor-views/layer-categories [category :default-layer])
        current-style-key (:current-style-key db)
        current-style-url (get map-engine/style-urls current-style-key)
        current-zoom (:map/zoom db)
        current-styles (style-editor-views/get-layer-styles default-layer current-style-url current-zoom)]
    (-> db
        (assoc :style-editor/selected-category category)
        (assoc :style-editor/target-layer default-layer)
        (assoc :style-editor/editing-style current-styles)
        (assoc :style-editor/navigation-history [])
        (assoc :style-editor/navigation-index -1))))

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
         current-zoom (:map/zoom db)
         current-styles (style-editor-views/get-layer-styles layer-id current-style-url current-zoom)
         zoom-level (style-editor-views/get-zoom-for-layer layer-id)]
     {:db (-> db
              (assoc :style-editor/target-layer layer-id)
              (assoc :style-editor/editing-style current-styles)
              (assoc :style-editor/navigation-history [])
              (assoc :style-editor/navigation-index -1))
      :dispatch [:style-editor/fly-to-feature layer-id zoom-level]})))

(re-frame/reg-event-fx
 :style-editor/fly-to-feature
 (fn [_ [_ layer-id zoom-level]]
   (map-engine/focus-on-layer layer-id zoom-level)
   {}))

(re-frame/reg-event-fx
 :style-editor/update-layer-style
 (fn [{:keys [db]} [_ target-layer style-key value zoom]]
   (let [current-zoom (or zoom (:map/zoom db))
         is-layout? (contains? (set style-editor-views/layout-style-keys) style-key)
         prop-type (if is-layout? "layout" "paint")
         processed-value (cond
                           (= style-key :text-font) value
                           (contains? style-editor-views/color-style-keys style-key) (style-editor-views/format-color-input value)
                           (or (contains? style-editor-views/opacity-style-keys style-key)
                               (contains? style-editor-views/width-style-keys style-key)
                               (= style-key :text-size)) (style-editor-views/format-numeric-input value)
                           (contains? #{:visibility :line-cap :line-join :text-transform :text-anchor :symbol-placement} style-key) (style-editor-views/format-enum-input value)
                           :else value)]
     (if (some? processed-value)
       (let [updated-value (map-engine/update-zoom-value-pair target-layer (name style-key) current-zoom processed-value prop-type)]
         {:dispatch [:style-editor/update-and-apply-style style-key updated-value]})
       {}))))

(re-frame/reg-event-fx
 :style-editor/update-style-debounced
 (fn [{:keys [db]} [_ target-layer style-key value zoom]]
   {:dispatch-debounce {:key (str "update-style-" target-layer "-" (name style-key))
                        :event [:style-editor/update-layer-style target-layer style-key value zoom]
                        :delay 300}}))

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
       (let [current-zoom (:map/zoom db)
             current-styles (style-editor-views/get-layer-styles target-layer (get map-engine/style-urls current-style-key) current-zoom)]
         {:db (assoc db :style-editor/editing-style current-styles)})))))

(re-frame/reg-event-fx
 :style-editor/fly-to-coords
 (fn [_ [_ coords]]
   (map-engine/fly-to-location coords 17)
   {}))

(re-frame/reg-event-fx
 :style-editor/zoom-and-retry-next
 (fn [_ [_ zoom]]
   (map-engine/zoom-to-level zoom #(re-frame/dispatch [:style-editor/navigate-next]))
   {}))

(re-frame/reg-event-fx
 :style-editor/navigate-next
 (fn [{:keys [db]} _]
   (let [layer-id (:style-editor/target-layer db)
         history (:style-editor/navigation-history db)
         index (:style-editor/navigation-index db)
         ^js map-obj (map-engine/get-map-instance)]
     (when (and layer-id map-obj)
       (if (< index (dec (count history)))
         ;; Case 1: Moving forward in existing history (Redo)
         (let [next-index (inc index)
               next-coords (nth history next-index)]
           {:db (assoc db :style-editor/navigation-index next-index)
            :dispatch [:style-editor/fly-to-coords next-coords]})

         ;; Case 2: Finding a new feature
         (let [;; If history is empty, record current position as start point
               current-center (if (empty? history)
                                (let [^js c (.getCenter map-obj)] [(.-lng c) (.-lat c)])
                                nil)
               ;; Prepare history for exclusion check
               temp-history (if current-center (conj history current-center) history)
               temp-index (if current-center 0 index)

               ;; Exclude all points currently in history to avoid loops
               excluded-set (set temp-history)

               ;; Find next nearest feature
               next-coords (map-engine/find-next-visible-feature layer-id excluded-set)]

           (if next-coords
             (let [new-history (conj temp-history next-coords)
                   new-index (inc temp-index)]
               {:db (-> db
                        (assoc :style-editor/navigation-history new-history)
                        (assoc :style-editor/navigation-index new-index))
                :dispatch [:style-editor/fly-to-coords next-coords]})

             ;; Not found. Check if we should zoom out and retry.
             (let [current-zoom (.getZoom map-obj)
                   target-zoom (style-editor-views/get-zoom-for-layer layer-id)]
               ;; If we are zoomed in closer than the target layer's ideal zoom (plus a buffer),
               ;; zoom out to that ideal zoom to broaden the search.
               (if (> current-zoom (+ target-zoom 0.5))
                 {:db (if current-center
                        (-> db
                            (assoc :style-editor/navigation-history temp-history)
                            (assoc :style-editor/navigation-index temp-index))
                        db)
                  :dispatch [:style-editor/zoom-and-retry-next target-zoom]}

                 ;; Already wide enough, just give up (or save state)
                 {:db (if current-center
                        (-> db
                            (assoc :style-editor/navigation-history temp-history)
                            (assoc :style-editor/navigation-index temp-index))
                        db)})))))))))

(re-frame/reg-event-fx
 :style-editor/navigate-prev
 (fn [{:keys [db]} _]
   (let [history (:style-editor/navigation-history db)
         index (:style-editor/navigation-index db)]
     (if (> index 0)
       (let [prev-index (dec index)
             prev-coords (nth history prev-index)]
         {:db (assoc db :style-editor/navigation-index prev-index)
          :dispatch [:style-editor/fly-to-coords prev-coords]})
       {}))))

(re-frame/reg-event-fx
 :style-editor/reset-to-defaults
 (fn [{:keys [db]} _]
   (let [current-style-key (:current-style-key db)]
     (if (= current-style-key :raster-style)
       ;; When entering raster mode, clear the editor state.
       {:db (-> db
                (assoc :style-editor/target-layer nil)
                (assoc :style-editor/editing-style nil)
                (assoc :style-editor/selected-category nil)
                (assoc :style-editor/navigation-history [])
                (assoc :style-editor/navigation-index -1))}
       ;; When entering a vector style, always re-initialize to default.
       {:db (init-to-default-db db)}))))
