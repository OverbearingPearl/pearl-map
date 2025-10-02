(ns pearl-map.features.style-editor.events
  (:require [re-frame.core :as re-frame]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.features.style-editor.views :as style-editor-views]))

;; Pure functions for state transformations
(defn- set-target-layer [db layer-id]
  (assoc db :style-editor/target-layer layer-id))

(defn- set-editing-style [db style]
  (assoc db :style-editor/editing-style style))

(defn- update-editing-style [db key value]
  (assoc-in db [:style-editor/editing-style key] value))

(defn- get-layer-styles [layer-id]
  (style-editor-views/get-layer-styles layer-id))

;; Layer state management
(re-frame/reg-event-db
 :style-editor/set-target-layer
 (fn [db [_ layer-id]]
   (set-target-layer db layer-id)))

(re-frame/reg-event-db
 :style-editor/set-editing-style
 (fn [db [_ style]]
   (set-editing-style db style)))

(re-frame/reg-event-db
 :style-editor/update-editing-style
 (fn [db [_ key value]]
   (update-editing-style db key value)))

(re-frame/reg-event-fx
 :style-editor/switch-target-layer
 (fn [{:keys [db]} [_ layer-id]]
   (let [current-styles (get-layer-styles layer-id)]
     {:db (-> db
              (set-target-layer layer-id)
              (set-editing-style current-styles))})))

(re-frame/reg-event-fx
 :style-editor/update-and-apply-style
 (fn [{:keys [db]} [_ style-key value]]
   (let [current-style (:style-editor/editing-style db)
         updated-style (if (= style-key :gradient)
                         ;; For gradient, we need to build the appropriate style expression
                         (let [gradient-data value
                               style (if (= (:type gradient-data) "uniform")
                                       ;; Uniform style - simple values
                                       {:fill-color (:uniform-color gradient-data)
                                        :fill-opacity (:uniform-opacity gradient-data)}
                                       ;; Gradient style - expression with stops
                                       (let [stops (sort-by :zoom (:stops gradient-data))
                                             ;; Build interpolate expressions with correct structure
                                             color-stops (mapcat (fn [stop]
                                                                   [(:zoom stop) (:color stop)]) stops)
                                             opacity-stops (mapcat (fn [stop]
                                                                     [(:zoom stop) (:opacity stop)]) stops)]
                                         ;; Ensure the expressions are in the correct MapLibre format
                                         {:fill-color (into ["interpolate" ["linear"] ["zoom"]] color-stops)
                                          :fill-opacity (into ["interpolate" ["linear"] ["zoom"]] opacity-stops)}))]
                           ;; Only merge the actual style properties, not the gradient metadata
                           (merge current-style style))
                         ;; For other keys, just update normally
                         (assoc current-style style-key value))]
     {:db (set-editing-style db (assoc updated-style :gradient value))
      :dispatch [:style-editor/apply-styles updated-style]})))

(re-frame/reg-event-fx
 :style-editor/set-and-apply-style
 (fn [{:keys [db]} [_ style]]
   {:db (set-editing-style db style)
    :dispatch [:style-editor/apply-styles style]}))

(re-frame/reg-event-fx
 :style-editor/apply-styles
 (fn [{:keys [db]} [_ style]]
   (style-editor-views/apply-current-style style)
   {:db db}))

(re-frame/reg-event-fx
 :style-editor/reset-styles-immediately
 (fn [{:keys [db]} _]
   (let [target-layer (get db :style-editor/target-layer "building")
         current-styles (get-layer-styles target-layer)]
     {:db (set-editing-style db current-styles)})))

(re-frame/reg-event-fx
 :style-editor/on-map-load
 (fn [{:keys [db]} _]
   (let [current-styles (style-editor-views/get-current-building-styles)]
     {:db (set-editing-style db current-styles)})))
