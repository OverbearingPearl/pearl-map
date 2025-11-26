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
 :style-editor/smart-find-feature
 (fn [{:keys [db]} [_ {:keys [layer-id excluded-set search-zoom on-found-event allow-zoom-retry?]}]]
   (let [^js map-obj (map-engine/get-map-instance)
         ;; Try find in current view
         next-coords (map-engine/find-next-visible-feature layer-id (or excluded-set #{}))]

     (if next-coords
       ;; Found! Dispatch success event with coords
       {:dispatch (conj on-found-event next-coords)}

       ;; Not found
       (if (and allow-zoom-retry? map-obj)
         (let [current-zoom (.getZoom map-obj)]
           (cond
             ;; Case 1: Zoom out if too deep
             (> current-zoom (+ search-zoom 0.5))
             {:dispatch [:style-editor/zoom-and-retry-find
                         {:zoom search-zoom
                          :retry-event [:style-editor/smart-find-feature
                                        {:layer-id layer-id
                                         :excluded-set excluded-set
                                         :search-zoom search-zoom
                                         :on-found-event on-found-event
                                         :allow-zoom-retry? false}]}]}

             ;; Case 2: Zoom in if too high (e.g. looking at country view, searching for park)
             (< current-zoom (- search-zoom 0.5))
             {:dispatch [:style-editor/zoom-and-retry-find
                         {:zoom search-zoom
                          :retry-event [:style-editor/smart-find-feature
                                        {:layer-id layer-id
                                         :excluded-set excluded-set
                                         :search-zoom search-zoom
                                         :on-found-event on-found-event
                                         :allow-zoom-retry? false}]}]}

             :else
             {}))
         ;; No retry allowed or map not ready
         {})))))

(re-frame/reg-event-fx
 :style-editor/zoom-and-retry-find
 (fn [_ [_ {:keys [zoom retry-event]}]]
   (map-engine/zoom-to-level zoom #(re-frame/dispatch retry-event))
   {}))

(re-frame/reg-event-fx
 :style-editor/switch-target-layer
 (fn [{:keys [db]} [_ layer-id]]
   (let [current-style-key (:current-style-key db)
         current-style-url (get map-engine/style-urls current-style-key)
         current-zoom (:map/zoom db)
         current-styles (style-editor-views/get-layer-styles layer-id current-style-url current-zoom)
         search-zoom (style-editor-views/get-zoom-for-layer layer-id)
         inspect-zoom (style-editor-views/get-inspect-zoom-for-layer layer-id)]
     {:db (-> db
              (assoc :style-editor/target-layer layer-id)
              (assoc :style-editor/editing-style current-styles)
              (assoc :style-editor/navigation-history [])
              (assoc :style-editor/navigation-index -1))
      :dispatch [:style-editor/smart-find-feature
                 {:layer-id layer-id
                  :excluded-set #{}
                  :search-zoom search-zoom
                  :on-found-event [:style-editor/on-switch-layer-found inspect-zoom]
                  :allow-zoom-retry? true}]})))

(re-frame/reg-event-fx
 :style-editor/on-switch-layer-found
 (fn [_ [_ search-zoom coords]]
   {:dispatch [:style-editor/fly-to-coords coords search-zoom]}))

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
 (fn [_ [_ coords zoom]]
   (map-engine/fly-to-location coords (or zoom map-engine/default-inspect-zoom))
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
               next-coords (nth history next-index)
               inspect-zoom (style-editor-views/get-inspect-zoom-for-layer layer-id)]
           {:db (assoc db :style-editor/navigation-index next-index)
            :dispatch [:style-editor/fly-to-coords next-coords inspect-zoom]})

         ;; Case 2: Finding a new feature
         (let [;; If history is empty, record current position as start point
               current-center (if (empty? history)
                                (let [^js c (.getCenter map-obj)] [(.-lng c) (.-lat c)])
                                nil)
               ;; Prepare history for exclusion check
               temp-history (if current-center (conj history current-center) history)
               temp-index (if current-center 0 index)

               excluded-set (set temp-history)
               search-zoom (style-editor-views/get-zoom-for-layer layer-id)
               inspect-zoom (style-editor-views/get-inspect-zoom-for-layer layer-id)]

           {:db (if current-center
                  (-> db
                      (assoc :style-editor/navigation-history temp-history)
                      (assoc :style-editor/navigation-index temp-index))
                  db)
            :dispatch [:style-editor/smart-find-feature
                       {:layer-id layer-id
                        :excluded-set excluded-set
                        :search-zoom search-zoom
                        :on-found-event [:style-editor/on-next-found inspect-zoom]
                        :allow-zoom-retry? true}]}))))))

(re-frame/reg-event-fx
 :style-editor/on-next-found
 (fn [{:keys [db]} [_ search-zoom coords]]
   (let [history (:style-editor/navigation-history db)
         index (:style-editor/navigation-index db)
         new-history (conj history coords)
         new-index (inc index)]
     {:db (-> db
              (assoc :style-editor/navigation-history new-history)
              (assoc :style-editor/navigation-index new-index))
      :dispatch [:style-editor/fly-to-coords coords search-zoom]})))

(re-frame/reg-event-fx
 :style-editor/navigate-prev
 (fn [{:keys [db]} _]
   (let [history (:style-editor/navigation-history db)
         index (:style-editor/navigation-index db)
         layer-id (:style-editor/target-layer db)]
     (if (> index 0)
       (let [prev-index (dec index)
             prev-coords (nth history prev-index)
             inspect-zoom (style-editor-views/get-inspect-zoom-for-layer layer-id)]
         {:db (assoc db :style-editor/navigation-index prev-index)
          :dispatch [:style-editor/fly-to-coords prev-coords inspect-zoom]})
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
