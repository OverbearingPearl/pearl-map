(ns pearl-map.features.style-editor.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [pearl-map.services.map-engine :as map-engine]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private style-keys
  [:fill-color :fill-opacity :fill-outline-color :fill-extrusion-color :fill-extrusion-opacity])

(def ^:private color-style-keys
  #{:fill-color :fill-outline-color :fill-extrusion-color})

(def ^:private opacity-style-keys
  #{:fill-opacity :fill-extrusion-opacity})

(def ^:private raster-style "raster-style")
(def ^:private building-layer "building")
(def ^:private building-top-layer "building-top")
(def ^:private extruded-building-layer "extruded-building")
(def ^:private extruded-building-top-layer "extruded-building-top")

(def ^:private flat-building-layers
  #{building-layer building-top-layer})

(def ^:private extruded-building-layers
  #{extruded-building-layer extruded-building-top-layer})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-current-zoom []
  (map-engine/get-current-zoom))

(defn- parse-style-value [style-key current-value current-zoom]
  (cond
    (contains? opacity-style-keys style-key)
    (map-engine/parse-numeric-expression current-value current-zoom)

    (contains? color-style-keys style-key)
    (map-engine/parse-color-expression current-value current-zoom)

    :else current-value))

(defn get-layer-styles [layer-id current-style]
  (if (= current-style raster-style)
    (zipmap style-keys (repeat :unsupported))
    (let [current-zoom (get-current-zoom)
          map-instance (map-engine/get-map-instance)]
      (if (and map-instance (map-engine/layer-exists? layer-id))
        (->> style-keys
             (map (fn [style-key]
                    (let [current-value (map-engine/get-paint-property layer-id (name style-key))]
                      [style-key (when (some? current-value)
                                   (parse-style-value style-key current-value current-zoom))])))
             (into {}))
        (zipmap style-keys (repeat nil))))))

(defn- format-color-input [value]
  (cond
    (nil? value) nil
    (= value "transparent") "transparent"
    (string? value) (if (str/starts-with? value "#")
                      value
                      (str "#" value))
    :else (str "#" (.toString (js/parseInt (str value)) 16))))

(defn- format-opacity-input [value]
  (when (some? value)
    (if (string? value)
      (let [parsed (js/parseFloat value)]
        (when-not (js/isNaN parsed) parsed))
      (when (number? value) value))))

(defn update-building-style
  ([target-layer style-key value]
   (update-building-style target-layer style-key value nil))
  ([target-layer style-key value zoom]
   (let [current-zoom (or zoom (get-current-zoom))
         processed-value (cond
                           (contains? color-style-keys style-key) (format-color-input value)
                           (contains? opacity-style-keys style-key) (format-opacity-input value)
                           :else value)]
     (when (some? processed-value)
       (let [updated-value (map-engine/update-zoom-value-pair target-layer (name style-key) current-zoom processed-value)]
         (re-frame/dispatch [:style-editor/update-and-apply-style style-key updated-value]))))))

(defn setup-map-listener []
  (when-let [^js/maplibregl.Map map-inst (map-engine/get-map-instance)]
    (doto map-inst
      (.off "load")
      (.off "styledata")
      (.off "idle")
      (.on "load" #(re-frame/dispatch [:style-editor/on-map-load]))
      (.on "idle" #(re-frame/dispatch [:style-editor/reset-styles-immediately])))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reagent Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- render-control-group [label & children]
  (into [:div {:style {:margin-bottom "10px"}}
         [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} label]]
        children))

(defn- render-color-input-with-overlay [{:keys [value on-change not-set-label]}]
  (let [is-transparent? (= value "transparent")
        is-not-set? (nil? value)
        display-value (if (or is-transparent? is-not-set?) "#f0f0f0" value)]
    [:div {:style {:position "relative"}}
     [:input {:type "color"
              :value display-value
              :on-change on-change
              :style {:width "100%" :height "30px" :border "1px solid #ddd" :border-radius "4px"
                      :opacity (if (or is-transparent? is-not-set?) 0.3 1.0)
                      :background "transparent"}}]
     (when (or is-transparent? is-not-set?)
       [:div {:style {:position "absolute" :top "0" :left "0" :right "0" :height "30px"
                      :display "flex" :align-items "center" :justify-content "center"
                      :background "repeating-linear-gradient(45deg, #ccc, #ccc 2px, #eee 2px, #eee 4px)"
                      :border-radius "4px" :color "#666" :font-weight "bold" :pointer-events "none"
                      :font-size "10px" :line-height "1"}}
        (if is-not-set? (or not-set-label "NOT SET") "TRANSPARENT")])]))

(defn- render-multi-zoom-color-controls [{:keys [zoom-pairs on-change-fn]}]
  [:div {:style {:display "flex" :gap "5px" :flex-wrap "wrap"}}
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "color-" zoom "-" index) :style {:position "relative" :flex "1" :min-width "60px"}}
      [render-color-input-with-overlay
       {:value value
        :on-change #(on-change-fn zoom (-> % .-target .-value))}]
      [:div {:style {:font-size "10px" :text-align "center" :margin-top "2px" :color "#666"}}
       (str "z" zoom)]])])

(defn- render-multi-zoom-opacity-controls [{:keys [zoom-pairs on-change-fn]}]
  [:div
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "opacity-" zoom "-" index) :style {:margin-bottom "10px"}}
      [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom "5px"}}
       [:span {:style {:font-size "11px" :color "#666"}} (str "z" zoom)]
       [:span {:style {:font-size "11px" :color "#666"}}
        (str (-> (or value 0) (* 100) js/Math.round) "%")]]
      [:input {:type "range"
               :min "0" :max "1" :step "0.1"
               :value (or value 0)
               :on-change #(on-change-fn zoom (-> % .-target .-value js/parseFloat))
               :style {:width "100%"}}]])])

(defn- render-single-opacity-control [{:keys [value on-change default-value label]}]
  [:div
   [:input {:type "range"
            :min "0" :max "1" :step "0.1"
            :value (or value default-value)
            :on-change on-change
            :style {:width "100%"}}]
   [:span {:style {:font-size "12px" :color "#666"}}
    (str "Current: " label)]])

(defn- render-unsupported-message []
  [:div {:style {:background "#fff3cd" :border "1px solid #ffeaa7" :padding "10px"
                 :border-radius "4px" :margin-bottom "15px"}}
   [:p {:style {:margin "0" :color "#856404" :font-size "12px" :font-weight "bold"}}
    "Style editing is not supported in Raster mode."]
   [:p {:style {:margin "5px 0 0 0" :color "#856404" :font-size "11px"}}
    "Switch to Dark or Light vector styles to edit building styles."]])

(defn- render-layer-not-exist-warning [{:keys [target-layer]}]
  [:div {:style {:background "#f8d7da" :border "1px solid #f5c6cb" :padding "10px"
                 :border-radius "4px" :margin-bottom "15px"}}
   [:p {:style {:margin "0" :color "#721c24" :font-size "12px" :font-weight "bold"}}
    (str "Layer '" target-layer "' does not exist in current style.")]
   [:p {:style {:margin "5px 0 0 0" :color "#721c24" :font-size "11px"}}
    "Style editing will not work for this layer. Please make sure you are using a vector style (Dark or Light)."]])

(defn- render-layer-selector [{:keys [target-layer]}]
  [:div {:style {:margin-bottom "15px"}}
   [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Target Layer"]
   [:select {:value target-layer
             :on-change #(let [new-layer (-> % .-target .-value)]
                           (re-frame/dispatch [:style-editor/set-target-layer new-layer])
                           (re-frame/dispatch [:style-editor/reset-styles-immediately]))
             :style {:width "100%" :padding "5px" :border "1px solid #ddd" :border-radius "4px"}}
    [:option {:value building-layer} "Building"]
    [:option {:value building-top-layer} "Building Top"]
    [:option {:value extruded-building-layer} "Building 3D (Extruded)"]
    [:option {:value extruded-building-top-layer} "Building 3D Top"]]])

(defn- render-status-info [{:keys [target-layer editing-style]}]
  [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
   [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
    (str "Current Layer: " target-layer)]
   [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
    "Only works with Dark or Light vector styles"]
   [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
    "Styles: " (pr-str (-> editing-style
                           (select-keys style-keys)
                           (update :fill-color #(or % "NOT SET"))
                           (update :fill-opacity #(or % "NOT SET"))
                           (update :fill-outline-color #(or % "NOT SET"))
                           (update :fill-extrusion-color #(or % "NOT SET"))
                           (update :fill-extrusion-opacity #(or % "NOT SET"))))]])

(defn- render-style-controls [{:keys [target-layer editing-style]}]
  (let [current-zoom (get-current-zoom)
        on-style-change (fn [style-key]
                          (fn [value] (update-building-style target-layer style-key value)))
        on-zoom-style-change (fn [style-key]
                               (fn [zoom value] (update-building-style target-layer style-key value zoom)))]
    [:div
     ;; Fill Color
     (when (contains? flat-building-layers target-layer)
       (let [zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-color" current-zoom)]
         [render-control-group "Fill Color"
          (if (> (count zoom-pairs) 1)
            [render-multi-zoom-color-controls
             {:zoom-pairs zoom-pairs
              :on-change-fn (on-zoom-style-change :fill-color)}]
            [render-color-input-with-overlay
             {:value (:fill-color editing-style)
              :on-change #((on-style-change :fill-color) (-> % .-target .-value))}])]))

     ;; Opacity
     (when (contains? flat-building-layers target-layer)
       (let [zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-opacity" current-zoom)]
         [render-control-group "Opacity"
          (if (> (count zoom-pairs) 1)
            [render-multi-zoom-opacity-controls
             {:zoom-pairs zoom-pairs
              :on-change-fn (on-zoom-style-change :fill-opacity)}]
            (let [opacity (:fill-opacity editing-style)
                  color-transparent? (= (:fill-color editing-style) "transparent")
                  display-value (if color-transparent? 0 opacity)
                  display-label (cond
                                  color-transparent? "0% (transparent)"
                                  (number? opacity) (str (-> opacity (* 100) js/Math.round) "%")
                                  :else "100% (default)")]
              [render-single-opacity-control
               {:value display-value
                :on-change #((on-style-change :fill-opacity) (-> % .-target .-value js/parseFloat))
                :default-value 1
                :label display-label}]))]))

     ;; Outline Color
     (when (= target-layer building-layer)
       [render-control-group "Outline Color"
        [render-color-input-with-overlay
         {:value (:fill-outline-color editing-style)
          :on-change #((on-style-change :fill-outline-color) (-> % .-target .-value))}]])

     ;; Extrusion Color
     (when (contains? extruded-building-layers target-layer)
       [render-control-group "Extrusion Color"
        [render-color-input-with-overlay
         {:value (:fill-extrusion-color editing-style)
          :on-change #((on-style-change :fill-extrusion-color) (-> % .-target .-value))}]])

     ;; Extrusion Opacity
     (when (contains? extruded-building-layers target-layer)
       (let [zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-extrusion-opacity" current-zoom)]
         [render-control-group "Extrusion Opacity"
          (if (> (count zoom-pairs) 1)
            [render-multi-zoom-opacity-controls
             {:zoom-pairs zoom-pairs
              :on-change-fn (on-zoom-style-change :fill-extrusion-opacity)}]
            (let [opacity (:fill-extrusion-opacity editing-style)
                  display-label (str (-> (or opacity 1) (* 100) js/Math.round) "%")]
              [render-single-opacity-control
               {:value opacity
                :on-change #((on-style-change :fill-extrusion-opacity) (-> % .-target .-value js/parseFloat))
                :default-value 1
                :label display-label}]))]))]))

(defn building-style-editor []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (setup-map-listener)
      (re-frame/dispatch [:style-editor/set-target-layer building-layer])
      (let [^js/maplibregl.Map map-inst (map-engine/get-map-instance)]
        (when (and map-inst (.isStyleLoaded map-inst))
          (re-frame/dispatch [:style-editor/reset-styles-immediately]))))
    :reagent-render
    (fn []
      (let [editing-style @(re-frame/subscribe [:style-editor/editing-style])
            target-layer @(re-frame/subscribe [:style-editor/target-layer])
            current-style-key @(re-frame/subscribe [:current-style-key])
            map-instance (map-engine/get-map-instance)
            layer-exists? (and map-instance (map-engine/layer-exists? target-layer))]
        [:div {:style {:background "rgba(255,255,255,0.98)"
                       :padding "18px"
                       :border-radius "10px"
                       :font-family "Arial, sans-serif"
                       :width "280px"
                       :box-shadow "0 4px 15px rgba(0,0,0,0.15)"}}
         [:h3 {:style {:margin "0 0 15px 0" :color "#333"}} "Building Style Editor"]

         (if (= current-style-key :raster-style)
           [render-unsupported-message]
           [:div
            (when-not layer-exists?
              [render-layer-not-exist-warning {:target-layer target-layer}])

            [render-layer-selector {:target-layer target-layer}]

            (when layer-exists?
              [:div
               [render-style-controls {:target-layer target-layer :editing-style editing-style}]
               [render-status-info {:target-layer target-layer :editing-style editing-style}]])])]))}))
