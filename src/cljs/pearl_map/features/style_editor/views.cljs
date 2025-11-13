(ns pearl-map.features.style-editor.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [pearl-map.services.map-engine :as map-engine]
            [pearl-map.utils.colors :as colors]))

(def ^:private paint-style-keys
  [:fill-color :fill-opacity :fill-outline-color :fill-extrusion-color :fill-extrusion-opacity
   :line-color :line-opacity :line-width :text-color :text-opacity :background-color :background-opacity])

(def ^:private layout-style-keys
  [:visibility :line-cap :line-join :text-field :text-size :text-transform :text-anchor :symbol-placement])

(def ^:private all-style-keys (vec (concat paint-style-keys layout-style-keys)))

(def ^:private color-style-keys
  #{:fill-color :fill-outline-color :fill-extrusion-color :line-color :text-color :background-color})

(def ^:private opacity-style-keys
  #{:fill-opacity :fill-extrusion-opacity :line-opacity :text-opacity :background-opacity})

(def ^:private width-style-keys
  #{:line-width})

(def ^:private raster-style "raster-style")
(def ^:private building-layer "building")
(def ^:private building-top-layer "building-top")
(def ^:private extruded-building-layer "extruded-building")
(def ^:private extruded-building-top-layer "extruded-building-top")

(def layer-categories
  {:transportation
   {:label "Transportation"
    :default-layer "road_pri_fill_noramp"
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
             "road_trunk_case_ramp" "road_trunk_fill-ramp"
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
    :default-layer "background"
    :layers ["background" "boundary_county" "boundary_state" "boundary_country_outline" "boundary_country_inner"]}
   :natural
   {:label "Natural Features"
    :default-layer "landcover"
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
    :default-layer "extruded-building"
    :layers ["building" "building-top" "extruded-building" "extruded-building-top"]}
   :labels
   {:label "Labels"
    :default-layer "poi_park"
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

(def ^:private label-layers
  (-> layer-categories :labels :layers set))

(def ^:private line-layers
  (clojure.set/union
   transportation-layers
   #{"boundary_county" "boundary_state" "boundary_country_outline" "boundary_country_inner" "waterway"}))

(def ^:private fill-layers
  (clojure.set/union
   #{"building" "building-top"}
   #{"landcover" "park_national_park" "park_nature_reserve" "landuse_residential" "landuse" "water" "water_shadow"}))

(defn- get-current-zoom []
  (map-engine/get-current-zoom))

(defn get-layers-for-category [category-key]
  (get-in layer-categories [category-key :layers]))

(defn- parse-style-value [style-key current-value current-zoom]
  (cond
    (or (contains? opacity-style-keys style-key)
        (contains? width-style-keys style-key)
        (= style-key :text-size))
    (map-engine/parse-numeric-expression current-value current-zoom)

    (contains? color-style-keys style-key)
    (map-engine/parse-color-expression current-value current-zoom)

    :else current-value))

(defn get-layer-styles [layer-id current-style]
  (if (= current-style raster-style)
    (zipmap all-style-keys (repeat :unsupported))
    (let [current-zoom (get-current-zoom)
          map-instance (map-engine/get-map-instance)]
      (if (and map-instance (map-engine/layer-exists? layer-id))
        (->> all-style-keys
             (map (fn [style-key]
                    (let [prop-type (if (contains? (set layout-style-keys) style-key) "layout" "paint")
                          get-fn (if (= prop-type "layout")
                                   map-engine/get-layout-property
                                   map-engine/get-paint-property)
                          current-value (get-fn layer-id (name style-key))
                          parsed-value (when (some? current-value)
                                         (parse-style-value style-key current-value current-zoom))]
                      [style-key (if (and (= style-key :visibility) (nil? parsed-value))
                                   "visible"
                                   parsed-value)])))
             (into {}))
        (zipmap all-style-keys (repeat nil))))))

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

(defn- format-enum-input [value]
  (when (some? value)
    (-> value str str/trim)))

(defn update-layer-style
  ([target-layer style-key value]
   (update-layer-style target-layer style-key value nil))
  ([target-layer style-key value zoom]
   (let [current-zoom (or zoom (get-current-zoom))
         is-layout? (contains? (set layout-style-keys) style-key)
         prop-type (if is-layout? "layout" "paint")
         processed-value (cond
                           (contains? color-style-keys style-key) (format-color-input value)
                           (or (contains? opacity-style-keys style-key)
                               (contains? width-style-keys style-key)
                               (= style-key :text-size)) (format-numeric-input value)
                           (contains? #{:visibility :line-cap :line-join :text-transform :text-anchor :symbol-placement} style-key) (format-enum-input value)
                           :else value)]
     (when (some? processed-value)
       (let [updated-value (map-engine/update-zoom-value-pair target-layer (name style-key) current-zoom processed-value prop-type)]
         (re-frame/dispatch [:style-editor/update-and-apply-style style-key updated-value]))))))

(defn setup-map-listener []
  (when-let [^js/maplibregl.Map map-inst (map-engine/get-map-instance)]
    (doto map-inst
      (.off "load")
      (.off "styledata")
      (.off "idle")
      (.on "styledata" #(re-frame/dispatch [:style-editor/on-map-load]))
      (.on "idle" #(re-frame/dispatch [:style-editor/reset-styles-immediately])))))

(defn- render-control-group [label & children]
  (into [:div {:class "control-group"}
         [:label {:class "control-label"} label]]
        children))

(defn- render-category-selector [{:keys [selected-category]}]
  [:div {:class "category-selector"}
   [:label {:class "control-label"} "Layer Category"]
   [:div {:class "category-selector-items"}
    (for [[category-key {:keys [label]}] layer-categories]
      [:label {:key category-key
               :class (str "category-item" (when (= selected-category category-key) " category-item-selected"))}
       [:input {:type "radio"
                :name "layer-category"
                :value (name category-key)
                :checked (= selected-category category-key)
                :on-change #(re-frame/dispatch [:style-editor/set-selected-category category-key])
                :class "category-item-radio"}]
       label])]])

(defn- render-color-input-with-overlay [{:keys [value on-change not-set-label]}]
  (let [formatted-value (format-color-input value)
        is-transparent? (= formatted-value "transparent")
        is-not-set? (nil? formatted-value)
        input-value (if (or is-transparent? is-not-set?) "#f0f0f0" formatted-value)]
    [:div {:class "color-input-overlay-container"}
     [:input {:type "color"
              :value input-value
              :on-change on-change
              :class (str "color-input-with-overlay" (when (or is-transparent? is-not-set?) " color-input-with-overlay-hidden"))}]
     (when (or is-transparent? is-not-set?)
       [:div {:class "color-input-overlay-content"}
        (if is-not-set? (or not-set-label "NOT SET") "TRANSPARENT")])]))

(defn- render-multi-zoom-color-controls [{:keys [zoom-pairs on-change-fn]}]
  [:div {:class "multi-zoom-controls"}
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "color-" zoom "-" index)
            :class "multi-zoom-item"}
      [render-color-input-with-overlay
       {:value value
        :on-change #(on-change-fn zoom (-> % .-target .-value))}]
      [:div {:class "multi-zoom-label"}
       (str "z" zoom)]])])

(defn- render-multi-zoom-opacity-controls [{:keys [zoom-pairs on-change-fn]}]
  [:div {:class "multi-zoom-controls"}
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "opacity-" zoom "-" index)
            :class "multi-zoom-item-opacity"}
      [:div {:class "multi-zoom-opacity-header"}
       [:span {:class "multi-zoom-opacity-label"} (str "z" zoom)]
       [:span {:class "multi-zoom-opacity-label"}
        (str (-> (or value 0) (* 100) js/Math.round) "%")]]
      [:input {:type "range"
               :min "0" :max "1" :step "0.1"
               :value (or value 0)
               :on-change #(on-change-fn zoom (-> % .-target .-value js/parseFloat))
               :class "slider-input"}]])])

(defn- render-single-opacity-control [{:keys [value on-change default-value label]}]
  [:div
   [:input {:type "range"
            :min "0" :max "1" :step "0.1"
            :value (or value default-value)
            :on-change on-change
            :class "slider-input"}]
   [:span {:class "single-value-label"}
    (str "Current: " label)]])

(defn- render-unsupported-message []
  [:div {:class "unsupported-message"}
   [:p {:class "unsupported-message-title"}
    "Style editing is not supported in Raster mode."]
   [:p {:class "unsupported-message-text"}
    "Switch to Dark or Light vector styles to edit building styles."]])

(defn- render-layer-not-exist-warning [{:keys [target-layer]}]
  [:div {:class "layer-warning"}
   [:p {:class "layer-warning-title"}
    (str "Layer '" target-layer "' does not exist in current style.")]
   [:p {:class "layer-warning-text"}
    "Style editing will not work for this layer. Please make sure you are using a vector style (Dark or Light)."]])

(defn- render-layer-selector [{:keys [target-layer selected-category]}]
  (let [available-layers (get-layers-for-category selected-category)]
    [:div {:class "layer-selector"}
     [:label {:class "control-label"} "Target Layer"]
     [:select {:key selected-category
               :value (or target-layer "") ; Use target-layer directly, if nil then use an empty string
               :on-change #(let [new-layer (-> % .-target .-value)]
                             (re-frame/dispatch [:style-editor/switch-target-layer new-layer]))
               :class "styled-select"}
      (for [layer-id (sort available-layers)] ; Ensure alphabetical sorting
        [:option {:key layer-id :value layer-id} layer-id])]]))

(defn- render-status-info [{:keys [target-layer editing-style]}]
  [:div {:class "status-info"}
   [:p {:class "status-info-layer"}
    (str "Current Layer: " target-layer)]
   [:p {:class "status-info-note"}
    "Only works with Dark or Light vector styles"]
   [:p {:class "status-info-styles"}
    "Styles: " (pr-str (select-keys editing-style all-style-keys))]])

(defn- render-enum-control [{:keys [label value options on-change]}]
  [render-control-group label
   [:select {:value (or value "")
             :on-change on-change
             :class "styled-select"}
    (for [option options]
      [:option {:key option :value option} option])]])

(defn- render-text-input-control [{:keys [label value on-change]}]
  [render-control-group label
   [:input {:type "text"
            :value (or value "")
            :on-change on-change
            :class "styled-select"}]])

(defn- render-multi-zoom-width-controls [{:keys [zoom-pairs on-change-fn]}]
  [:div {:class "multi-zoom-controls"}
   (for [[index {:keys [zoom value]}] (map-indexed vector zoom-pairs)]
     [:div {:key (str "width-" zoom "-" index)
            :class "multi-zoom-item-opacity"}
      [:div {:class "multi-zoom-opacity-header"}
       [:span {:class "multi-zoom-opacity-label"} (str "z" zoom)]
       [:span {:class "multi-zoom-opacity-label"}
        (str (.toFixed (or value 0) 1) "px")]]
      [:input {:type "range"
               :min "0" :max "20" :step "0.5"
               :value (or value 0)
               :on-change #(on-change-fn zoom (-> % .-target .-value js/parseFloat))
               :class "slider-input"}]])])

(defn- render-single-width-control [{:keys [value on-change default-value label]}]
  [:div
   [:input {:type "range"
            :min "0" :max "20" :step "0.5"
            :value (or value default-value)
            :on-change on-change
            :class "slider-input"}]
   [:span {:class "single-value-label"}
    (str "Current: " label)]])

(defn- render-line-color-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "line-color" current-zoom "paint")]
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
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "line-opacity" current-zoom "paint")]
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
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "line-width" current-zoom "paint")]
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

(defn- render-text-color-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "text-color" current-zoom "paint")]
    [render-control-group "Text Color"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-color-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :text-color)}]
       [render-color-input-with-overlay
        {:value (:text-color editing-style)
         :on-change #((on-style-change :text-color) (-> % .-target .-value))}])]))

(defn- render-text-opacity-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "text-opacity" current-zoom "paint")]
    [render-control-group "Text Opacity"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-opacity-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :text-opacity)}]
       (let [opacity (:text-opacity editing-style)
             display-label (str (-> (or opacity 1) (* 100) js/Math.round) "%")]
         [render-single-opacity-control
          {:value opacity
           :on-change #((on-style-change :text-opacity) (-> % .-target .-value js/parseFloat))
           :default-value 1
           :label display-label}]))]))

(defn- render-fill-color-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-color" current-zoom "paint")]
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
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-opacity" current-zoom "paint")]
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
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "fill-extrusion-opacity" current-zoom "paint")]
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

(defn- render-background-color-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "background-color" current-zoom "paint")]
    [render-control-group "Background Color"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-color-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :background-color)}]
       [render-color-input-with-overlay
        {:value (:background-color editing-style)
         :on-change #((on-style-change :background-color) (-> % .-target .-value))}])]))

(defn- render-background-opacity-control [{:keys [target-layer editing-style on-style-change on-zoom-style-change]}]
  (let [current-zoom (get-current-zoom)
        zoom-pairs (map-engine/get-zoom-value-pairs target-layer "background-opacity" current-zoom "paint")]
    [render-control-group "Background Opacity"
     (if (> (count zoom-pairs) 1)
       [render-multi-zoom-opacity-controls
        {:zoom-pairs zoom-pairs
         :on-change-fn (on-zoom-style-change :background-opacity)}]
       (let [opacity (:background-opacity editing-style)
             display-label (str (-> (or opacity 1) (* 100) js/Math.round) "%")]
         [render-single-opacity-control
          {:value opacity
           :on-change #((on-style-change :background-opacity) (-> % .-target .-value js/parseFloat))
           :default-value 1
           :label display-label}]))]))

(defn- render-style-controls [{:keys [target-layer editing-style on-style-change]}]
  (let [on-change-event (fn [style-key]
                          (fn [event] (update-layer-style target-layer style-key (-> event .-target .-value))))
        on-zoom-style-change (fn [style-key]
                               (fn [zoom value] (update-layer-style target-layer style-key value zoom)))
        control-props {:target-layer target-layer
                       :editing-style editing-style
                       :on-style-change on-style-change
                       :on-zoom-style-change on-zoom-style-change}]
    [:div
     (when (contains? fill-layers target-layer)
       [:<>
        ^{:key "fill-color-control"} [render-fill-color-control control-props]
        ^{:key "fill-opacity-control"} [render-fill-opacity-control control-props]])

     (when (= target-layer building-layer)
       [render-outline-color-control control-props])

     (when (contains? #{"extruded-building" "extruded-building-top"} target-layer)
       [:<>
        ^{:key "extrusion-color-control"} [render-extrusion-color-control control-props]
        ^{:key "extrusion-opacity-control"} [render-extrusion-opacity-control control-props]])

     (when (contains? line-layers target-layer)
       [:<>
        ^{:key "line-layout-controls"}
        [:div
         [render-enum-control {:label "Line Cap"
                               :value (:line-cap editing-style)
                               :options ["butt" "round" "square"]
                               :on-change (on-change-event :line-cap)}]
         [render-enum-control {:label "Line Join"
                               :value (:line-join editing-style)
                               :options ["bevel" "round" "miter"]
                               :on-change (on-change-event :line-join)}]]
        ^{:key "line-color-control"} [render-line-color-control control-props]
        ^{:key "line-opacity-control"} [render-line-opacity-control control-props]
        ^{:key "line-width-control"} [render-line-width-control control-props]])

     (when (contains? label-layers target-layer)
       [:<>
        ^{:key "label-layout-controls"}
        [:div
         [render-text-input-control {:label "Text Field"
                                     :value (:text-field editing-style)
                                     :on-change (on-change-event :text-field)}]
         ;; NOTE: text-size is numeric and can have zoom stops, so we reuse width/opacity controls logic
         ;; For simplicity, we'll add a single value editor here. A multi-zoom one would be better.
         [render-control-group "Text Size"
          [:input {:type "number" :min 0 :step 1
                   :value (or (:text-size editing-style) 12)
                   :on-change (on-change-event :text-size)
                   :class "styled-select"}]]]
        ^{:key "text-color-control"} [render-text-color-control control-props]
        ^{:key "text-opacity-control"} [render-text-opacity-control control-props]])

     (when (= target-layer "background")
       [:<>
        ^{:key "background-color-control"} [render-background-color-control control-props]
        ^{:key "background-opacity-control"} [render-background-opacity-control control-props]])]))

(defn style-editor []
  (reagent/create-class
   {:component-did-mount
    (fn []
      (re-frame/dispatch [:style-editor/initialize])
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
      (setup-map-listener)
      (let [editing-style @(re-frame/subscribe [:style-editor/editing-style])
            target-layer @(re-frame/subscribe [:style-editor/target-layer])
            selected-category @(re-frame/subscribe [:style-editor/selected-category])
            current-style-key @(re-frame/subscribe [:current-style-key])
            ^js/maplibregl.Map map-instance (map-engine/get-map-instance)
            style-loaded? (and map-instance (.isStyleLoaded map-instance))
            layer-exists? (and style-loaded? target-layer (map-engine/layer-exists? target-layer))]

        [:div {:class "style-editor-scrollable"}
         [:h3 {:class "style-editor-title"} "Style Editor"]

         (if (= current-style-key :raster-style)
           [render-unsupported-message]
           (if-not style-loaded?
             [:div {:class "layer-warning"}
              [:p {:class "layer-warning-title"} "Style is loading..."]
              [:p {:class "layer-warning-text"} "Please wait for the map style to finish loading."]]
             [:div
              [render-category-selector {:selected-category selected-category}]
              [render-layer-selector {:target-layer target-layer :selected-category selected-category}]

              (when (nil? target-layer)
                [:div {:class "layer-warning"}
                 [:p {:class "layer-warning-title"}
                  "No layer selected or available for editing."]
                 [:p {:class "layer-warning-text"}
                  "Please select a layer from the dropdown above."]])

              (when (and target-layer (not layer-exists?))
                [render-layer-not-exist-warning {:target-layer target-layer}])

              (when (and target-layer layer-exists?)
                (let [on-style-change (fn [style-key]
                                        (fn [value] (update-layer-style target-layer style-key value)))]
                  [:div
                   [:div {:class "visibility-toggle"}
                    [:input {:type "checkbox"
                             :id "layer-visibility-checkbox"
                             :checked (= (:visibility editing-style) "visible")
                             :on-change (fn [e]
                                          (let [is-checked (-> e .-target .-checked)
                                                new-visibility (if is-checked "visible" "none")]
                                            ((on-style-change :visibility) new-visibility)))
                             :class "visibility-checkbox"}]
                    [:label {:for "layer-visibility-checkbox"
                             :class "visibility-label"}
                     "Visible"]]
                   [render-style-controls {:target-layer target-layer
                                           :editing-style editing-style
                                           :on-style-change on-style-change}]
                   [render-status-info {:target-layer target-layer :editing-style editing-style}]]))]
             ))]))}))
