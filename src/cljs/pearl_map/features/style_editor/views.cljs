(ns pearl-map.features.style-editor.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.utils.colors :as colors]))

(def ^:private style-keys
  [:fill-color :fill-opacity :fill-outline-color :fill-extrusion-color :fill-extrusion-opacity
   :line-color :line-opacity :line-width])

(def ^:private color-style-keys
  #{:fill-color :fill-outline-color :fill-extrusion-color :line-color})

(def ^:private opacity-style-keys
  #{:fill-opacity :fill-extrusion-opacity :line-opacity})

(def ^:private width-style-keys
  #{:line-width})

(def ^:private raster-style "raster-style")
(def ^:private building-layer "building")
(def ^:private building-top-layer "building-top")
(def ^:private extruded-building-layer "extruded-building")
(def ^:private extruded-building-top-layer "extruded-building-top")

(def ^:private layer-categories
  {:transportation
   {:label "Transportation"
    :layers [;; Aeroway
             "aeroway-runway" "aeroway-taxiway"
             ;; Tunnels
             "tunnel_path"
             "tunnel_service_case" "tunnel_service_fill"
             "tunnel_minor_case" "tunnel_minor_fill"
             "tunnel_sec_case" "tunnel_sec_fill"
             "tunnel_pri_case" "tunnel_pri_fill"
             "tunnel_trunk_case" "tunnel_trunk_fill"
             "tunnel_mot_case" "tunnel_mot_fill"
             "tunnel_rail" "tunnel_rail_dash"
             ;; Roads
             "road_path"
             "road_service_case" "road_service_fill"
             "road_minor_case" "road_minor_fill"
             "road_sec_case_noramp" "road_sec_fill_noramp"
             "road_pri_case_noramp" "road_pri_fill_noramp"
             "road_pri_case_ramp" "road_pri_fill_ramp"
             "road_trunk_case_noramp" "road_trunk_fill_noramp"
             "road_trunk_case_ramp" "road_trunk_fill_ramp"
             "road_mot_case_noramp" "road_mot_fill_noramp"
             "road_mot_case_ramp" "road_mot_fill_ramp"
             ;; Railways
             "rail" "rail_dash"
             ;; Bridges
             "bridge_path"
             "bridge_service_case" "bridge_service_fill"
             "bridge_minor_case" "bridge_minor_fill"
             "bridge_sec_case" "bridge_sec_fill"
             "bridge_pri_case" "bridge_pri_fill"
             "bridge_trunk_case" "bridge_trunk_fill"
             "bridge_mot_case" "bridge_mot_fill"]}
   :boundaries
   {:label "Background & Boundaries"
    :layers ["background" "boundary_county" "boundary_state" "boundary_country_outline" "boundary_country_inner"]}
   :natural
   {:label "Natural Features"
    :layers ["landcover"
             "park_national_park"
             "park_nature_reserve"
             "landuse_residential"
             "landuse"
             "waterway"
             "water"
             "water_shadow"]}
   :buildings
   {:label "Buildings"
    :layers ["building" "building-top" "extruded-building" "extruded-building-top"]}
   :labels
   {:label "Labels"
    :layers [;; Water labels
             "waterway_label"
             "watername_ocean"
             "watername_sea"
             "watername_lake"
             "watername_lake_line"
             ;; Place labels
             "place_hamlet"
             "place_suburbs"
             "place_villages"
             "place_town"
             "place_country_2"
             "place_country_1"
             "place_state"
             "place_continent"
             "place_city_r6"
             "place_city_r5"
             "place_city_dot_r7"
             "place_city_dot_r4"
             "place_city_dot_r2"
             "place_city_dot_z7"
             "place_capital_dot_z7"
             ;; POI labels
             "poi_stadium"
             "poi_park"
             ;; Road labels
             "roadname_minor"
             "roadname_sec"
             "roadname_pri"
             "roadname_major"
             ;; House numbers
             "housenumber"]}})

(def ^:private transportation-layers
  (-> layer-categories :transportation :layers set))

(defn- get-current-zoom []
  (map-engine/get-current-zoom))

(defn get-layers-for-category [category-key]
  (get-in layer-categories [category-key :layers]))

(defn- parse-style-value [style-key current-value current-zoom]
  (cond
    (or (contains? opacity-style-keys style-key)
        (contains? width-style-keys style-key))
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
  (let [s-val (when (some? value) (-> value str str/trim))]
    (cond
      (str/blank? s-val) nil
      (= s-val "transparent") "transparent"
      (str/starts-with? s-val "#")
      (let [hex-val (str/replace s-val "#" "")]
        (cond
          (= (count hex-val) 3) (str "#" (apply str (mapcat #(repeat 2 %) hex-val))) ;; #ddd -> #dddddd
          (= (count hex-val) 6) (str "#" hex-val)
          :else nil)) ;; Invalid hex length
      (str/starts-with? s-val "rgb")
      (if-let [rgba (colors/parse-rgba-string s-val)]
        (colors/rgba-to-hex rgba)
        nil)
      :else nil)))

(defn- format-numeric-input [value]
  (when (some? value)
    (if (string? value)
      (let [parsed (js/parseFloat value)]
        (when-not (js/isNaN parsed) parsed))
      (when (number? value) value))))

(defn update-layer-style
  ([target-layer style-key value]
   (update-layer-style target-layer style-key value nil))
  ([target-layer style-key value zoom]
   (let [current-zoom (or zoom (get-current-zoom))
         processed-value (cond
                           (contains? color-style-keys style-key) (format-color-input value)
                           (or (contains? opacity-style-keys style-key)
                               (contains? width-style-keys style-key)) (format-numeric-input value)
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

(defn- render-control-group [label & children]
  (into [:div {:style {:margin-bottom "10px"}}
         [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} label]]
        children))

(defn- render-category-selector [{:keys [selected-category]}]
  [:div {:style {:margin-bottom "15px"}}
   [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Layer Category"]
   [:div {:style {:display "flex" :flex-direction "column" :gap "5px"}}
    (for [[category-key {:keys [label]}] layer-categories]
      [:label {:key category-key
               :style {:display "flex" :align-items "center" :cursor "pointer"
                       :background (if (= selected-category category-key) "#e0e0e0" "#f5f5f5")
                       :padding "4px 8px" :border-radius "4px" :font-size "11px"}}
       [:input {:type "radio"
                :name "layer-category"
                :value (name category-key)
                :checked (= selected-category category-key)
                :on-change #(re-frame/dispatch [:style-editor/set-selected-category category-key])
                :style {:margin-right "5px"}}]
       label])]])

(defn- render-color-input-with-overlay [{:keys [value on-change not-set-label]}]
  (let [formatted-value (format-color-input value)
        is-transparent? (= formatted-value "transparent")
        is-not-set? (nil? formatted-value)
        input-value (if (or is-transparent? is-not-set?) "#f0f0f0" formatted-value)]
    [:div {:style {:position "relative"}}
     [:input {:type "color"
              :value input-value
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
  [:div {:style {:display "flex"
                 :gap "8px"
                 :overflow-x "auto"
                 :padding-bottom "8px"
                 :scrollbar-width "thin"
                 :scrollbar-color "#ddd transparent"}}
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "color-" zoom "-" index)
            :style {:flex "0 0 120px" :position "relative"}}
      [render-color-input-with-overlay
       {:value value
        :on-change #(on-change-fn zoom (-> % .-target .-value))}]
      [:div {:style {:font-size "10px" :text-align "center" :margin-top "2px" :color "#666"}}
       (str "z" zoom)]])])

(defn- render-multi-zoom-opacity-controls [{:keys [zoom-pairs on-change-fn]}]
  [:div {:style {:display "flex"
                 :gap "8px"
                 :overflow-x "auto"
                 :padding-bottom "8px"
                 :scrollbar-width "thin"
                 :scrollbar-color "#ddd transparent"}}
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "opacity-" zoom "-" index)
            :style {:flex "0 0 120px" :margin-bottom "10px"}}
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

(defn- render-layer-selector [{:keys [target-layer selected-category]}]
  (let [available-layers (get-layers-for-category selected-category)]
    [:div {:style {:margin-bottom "15px"}}
     [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Target Layer"]
     [:select {:value target-layer
               :on-change #(let [new-layer (-> % .-target .-value)]
                             (re-frame/dispatch [:style-editor/switch-target-layer new-layer]))
               :style {:width "100%" :padding "5px" :border "1px solid #ddd" :border-radius "4px"}}
      (for [layer-id available-layers]
        [:option {:key layer-id :value layer-id} layer-id])]]))

(defn- render-status-info [{:keys [target-layer editing-style]}]
  [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
   [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
    (str "Current Layer: " target-layer)]
   [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
    "Only works with Dark or Light vector styles"]
   [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
    "Styles: " (pr-str (select-keys editing-style style-keys))]])

(defn- render-multi-zoom-width-controls [{:keys [zoom-pairs on-change-fn]}]
  [:div {:style {:display "flex"
                 :gap "8px"
                 :overflow-x "auto"
                 :padding-bottom "8px"
                 :scrollbar-width "thin"
                 :scrollbar-color "#ddd transparent"}}
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "width-" zoom "-" index)
            :style {:flex "0 0 120px" :margin-bottom "10px"}}
      [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom "5px"}}
       [:span {:style {:font-size "11px" :color "#666"}} (str "z" zoom)]
       [:span {:style {:font-size "11px" :color "#666"}}
        (str (.toFixed (or value 0) 1) "px")]]
      [:input {:type "range"
               :min "0" :max "20" :step "0.5"
               :value (or value 0)
               :on-change #(on-change-fn zoom (-> % .-target .-value js/parseFloat))
               :style {:width "100%"}}]])])

(defn- render-single-width-control [{:keys [value on-change default-value label]}]
  [:div
   [:input {:type "range"
            :min "0" :max "20" :step "0.5"
            :value (or value default-value)
            :on-change on-change
            :style {:width "100%"}}]
   [:span {:style {:font-size "12px" :color "#666"}}
    (str "Current: " label)]])

(defn- render-line-color-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "line-color" current-zoom)]
    [render-control-group "Line Color"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-color-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :line-color)}]
       [render-color-input-with-overlay
        {:value (:line-color editing-style)
         :on-change #((on-style-change :line-color) (-> % .-target .-value))}])]))

(defn- render-line-opacity-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "line-opacity" current-zoom)]
    [render-control-group "Line Opacity"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-opacity-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :line-opacity)}]
       (let [opacity (:line-opacity editing-style)
             display-label (str (-> (or opacity 1) (* 100) js/Math.round) "%")]
         [render-single-opacity-control
          {:value opacity
           :on-change #((on-style-change :line-opacity) (-> % .-target .-value js/parseFloat))
           :default-value 1
           :label display-label}]))]))

(defn- render-line-width-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "line-width" current-zoom)]
    [render-control-group "Line Width"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-width-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :line-width)}]
       (let [width (:line-width editing-style)
             display-label (if (number? width) (str (.toFixed width 1) "px") "default")]
         [render-single-width-control
          {:value width
           :on-change #((on-style-change :line-width) (-> % .-target .-value js/parseFloat))
           :default-value 1
           :label display-label}]))]))

(defn- render-fill-color-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-color" current-zoom)]
    [render-control-group "Fill Color"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-color-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :fill-color)}]
       [render-color-input-with-overlay
        {:value (:fill-color editing-style)
         :on-change #((on-style-change :fill-color) (-> % .-target .-value))}])]))

(defn- render-fill-opacity-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-opacity" current-zoom)]
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

(defn- render-outline-color-control [{:keys [editing-style on-style-change]}]
  [render-control-group "Outline Color"
   [render-color-input-with-overlay
    {:value (:fill-outline-color editing-style)
     :on-change #((on-style-change :fill-outline-color) (-> % .-target .-value))}]])

(defn- render-extrusion-color-control [{:keys [editing-style on-style-change]}]
  [render-control-group "Extrusion Color"
   [render-color-input-with-overlay
    {:value (:fill-extrusion-color editing-style)
     :on-change #((on-style-change :fill-extrusion-color) (-> % .-target .-value))}]])

(defn- render-extrusion-opacity-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-extrusion-opacity" current-zoom)]
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
           :label display-label}]))]))

(defn- render-style-controls [{:keys [target-layer editing-style]}]
  (let [on-style-change (fn [style-key]
                          (fn [value] (update-layer-style target-layer style-key value)))
        on-zoom-style-change (fn [style-key]
                               (fn [zoom value] (update-layer-style target-layer style-key value zoom)))
        control-props {:target-layer target-layer
                       :editing-style editing-style
                       :on-style-change on-style-change
                       :on-zoom-style-change on-zoom-style-change}]
    [:div
     (when (contains? #{"building" "building-top"} target-layer)
       [:<>
        ^{:key "fill-color-control"} [render-fill-color-control control-props]
        ^{:key "fill-opacity-control"} [render-fill-opacity-control control-props]])

     (when (= target-layer building-layer)
       [render-outline-color-control control-props])

     (when (contains? #{"extruded-building" "extruded-building-top"} target-layer)
       [:<>
        ^{:key "extrusion-color-control"} [render-extrusion-color-control control-props]
        ^{:key "extrusion-opacity-control"} [render-extrusion-opacity-control control-props]])

     (when (contains? transportation-layers target-layer)
       [:<>
        ^{:key "line-color-control"} [render-line-color-control control-props]
        ^{:key "line-opacity-control"} [render-line-opacity-control control-props]
        ^{:key "line-width-control"} [render-line-width-control control-props]])]))

(defn style-editor []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (setup-map-listener)
      (re-frame/dispatch [:style-editor/set-selected-category :buildings])
      (let [^js/maplibregl.Map map-inst (map-engine/get-map-instance)]
        (when (and map-inst (.isStyleLoaded map-inst))
          (re-frame/dispatch [:style-editor/reset-styles-immediately])))
      ;; Dynamically inject Webkit scrollbar styles
      (let [style-el (.createElement js/document "style")]
        (set! (.-textContent style-el)
              ".style-editor-scrollable::-webkit-scrollbar { width: 6px; height: 6px; }
               .style-editor-scrollable::-webkit-scrollbar-thumb { background: #ddd; border-radius: 3px; }")
        (.appendChild js/document.head style-el)))
    :component-will-unmount
    (fn []
      ;; Remove dynamically injected styles
      (doseq [style-el (js/document.querySelectorAll "style")]
        (when (str/includes? (.-textContent style-el) ".style-editor-scrollable::-webkit-scrollbar")
          (.remove style-el))))
    :reagent-render
    (fn []
      (let [editing-style @(re-frame/subscribe [:style-editor/editing-style])
            target-layer @(re-frame/subscribe [:style-editor/target-layer])
            selected-category @(re-frame/subscribe [:style-editor/selected-category])
            current-style-key @(re-frame/subscribe [:current-style-key])
            map-instance (map-engine/get-map-instance)
            layer-exists? (and map-instance (map-engine/layer-exists? target-layer))]
        [:div {:class "style-editor-scrollable"
               :style {:background "rgba(255,255,255,0.98)"
                       :padding "18px"
                       :border-radius "10px"
                       :font-family "Arial, sans-serif"
                       :width "280px"
                       :box-shadow "0 4px 15px rgba(0,0,0,0.15)"
                       :max-height "70vh"
                       :overflow-y "auto"
                       :scrollbar-width "thin"
                       :scrollbar-color "#ddd transparent"}}
         [:h3 {:style {:margin "0 0 15px 0" :color "#333"}} "Style Editor"]

         (if (= current-style-key :raster-style)
           [render-unsupported-message]
           [:div
            (when-not layer-exists?
              [render-layer-not-exist-warning {:target-layer target-layer}])

            [render-category-selector {:selected-category selected-category}]
            [render-layer-selector {:target-layer target-layer :selected-category selected-category}]

            (when layer-exists?
              [:div
               [render-style-controls {:target-layer target-layer :editing-style editing-style}]
               [render-status-info {:target-layer target-layer :editing-style editing-style}]])])]))}))
