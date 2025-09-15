(ns pearl-map.editor
  (:require [reagent.core :as reagent]
            [clojure.string :as str])
  (:require ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]))

;; Default building style configurations
(def default-building-styles
  {:light {:fill-color "#f0f0f0"
           :fill-opacity 0.7
           :fill-outline-color "#cccccc"}
   :dark {:fill-color "#2d3748"
          :fill-opacity 0.8
          :fill-outline-color "#4a5568"}})

;; Add debug function to check style-spec module
(defn debug-style-spec-module []
  "Debug function to check style-spec module availability"
  (js/console.log "=== DEBUG: style-spec module analysis ===")
  (js/console.log "style-spec module:" style-spec)
  (when style-spec
    (js/console.log "Module type:" (type style-spec))
    (js/console.log "Available keys:" (js/Object.keys style-spec))
    (js/console.log "validateStyleMin function:" (.-validateStyleMin style-spec))
    (js/console.log "validateStyleMin type:" (when (.-validateStyleMin style-spec) (type (.-validateStyleMin style-spec))))))

;; Check module when component loads
(defn check-style-spec-on-load []
  (js/setTimeout debug-style-spec-module 2000))

(defn validate-style [style]
  "Validate style using official MapLibre validation"
  (try
    (js/console.log "=== Starting style validation ===")
    (js/console.log "Style to validate:" (clj->js style))

    ;; Use official validation directly, assuming it's always available
    (let [complete-style (clj->js
                          {:version 8
                           :name "Building Style Validation"
                           :sources {:dummy-source {:type "geojson"
                                                    :data {:type "FeatureCollection"
                                                           :features []}}}
                           :layers [{:id "validation-layer"
                                     :type "fill"
                                     :source "dummy-source"
                                     :paint style}]})
          validation-result ((.-validateStyleMin style-spec) complete-style)]

      (js/console.log "Validation result:" validation-result)

      ;; validateStyleMin returns error array, empty array means validation passed
      (if (and (array? validation-result) (== (.-length validation-result) 0))
        true
        (do
          (js/console.error "Validation errors:" validation-result)
          false)))

    (catch js/Error e
      (js/console.error "Style validation failed:" e)
      false)))

;; Shared MapLibre parsing utilities
(defn get-current-zoom []
  "Get current zoom level from map instance"
  (let [map-inst (.-pearlMapInstance js/window)]
    (when map-inst (.getZoom map-inst))))

(defn parse-maplibre-stops [stops-obj]
  "Parse MapLibre stops format and return interpolated value"
  (let [current-zoom (get-current-zoom)
        stops (.-stops stops-obj)
        sorted-stops (sort-by first (js->clj stops))]
    (if (empty? sorted-stops)
      (throw (js/Error. "Empty stops array"))
      (let [first-stop (first sorted-stops)
            last-stop (last sorted-stops)]
        (cond
          (<= current-zoom (first first-stop)) (second first-stop)
          (>= current-zoom (first last-stop)) (second last-stop)
          :else (loop [[[z1 v1] & rest-stops] sorted-stops]
                  (when (and rest-stops (first (first rest-stops)))
                    (let [[z2 v2] (first rest-stops)]
                      (if (and (>= current-zoom z1) (< current-zoom z2))
                        (+ v1 (* (- current-zoom z1) (/ (- v2 v1) (- z2 z1))))
                        (recur rest-stops))))))))))

(defn parse-maplibre-expression [expr-obj]
  "Parse MapLibre expression format and return interpolated value"
  (let [current-zoom (get-current-zoom)
        expr (.-expression expr-obj)]
    (if (and (array? expr)
             (= (aget expr 0) "interpolate")
             (= (aget expr 1) "linear")
             (= (aget expr 2) "zoom"))
      (let [stops (drop 3 (array-seq expr))
            stop-pairs (partition 2 stops)]
        (if (empty? stop-pairs)
          (throw (js/Error. "Empty expression stops"))
          (let [first-pair (first stop-pairs)
                last-pair (last stop-pairs)]
            (cond
              (<= current-zoom (first first-pair)) (second first-pair)
              (>= current-zoom (first last-pair)) (second last-pair)
              :else (loop [[[z1 v1] [z2 v2] & more] stop-pairs]
                      (if (and z2 (>= current-zoom z1) (< current-zoom z2))
                        (+ v1 (* (- current-zoom z1) (/ (- v2 v1) (- z2 z1))))
                        (recur (cons [z2 v2] more))))))))
      (throw (js/Error. (str "Unsupported expression format: " (js/JSON.stringify expr)))))))

;; Color conversion functions
(defn rgba-to-hex [color-value]
  "Convert color value to hex format - handle various MapLibre color formats"
  (try
    (cond
      ;; 1. Already a hex string
      (and (string? color-value) (str/starts-with? color-value "#"))
      color-value

      ;; 2. RGBA/RGB string format
      (and (string? color-value) (or (str/includes? color-value "rgba")
                                     (str/includes? color-value "rgb")))
      (-> (color color-value)
          (.hex)
          (.toString)
          (.toLowerCase))

      ;; 3. MapLibre expression format: ["interpolate", "linear", "zoom", ...]
      (and (object? color-value) (.-expression color-value))
      (let [color-stop (parse-maplibre-expression color-value)]
        (if (string? color-stop)
          color-stop
          (throw (js/Error. "No valid color stops found in expression"))))

      ;; 4. MapLibre stops format: {"stops": [[zoom, color], ...]}
      (and (object? color-value) (.-stops color-value))
      (let [color-stop (parse-maplibre-stops color-value)]
        (if (string? color-stop)
          color-stop
          (throw (js/Error. "No valid color stops found in stops array"))))

      ;; 5. Other object formats - try to convert to string
      (object? color-value)
      (let [str-value (str color-value)]
        (if (str/starts-with? str-value "#")
          str-value
          (throw (js/Error. (str "Unsupported color object format: " (js/JSON.stringify color-value))))))

      ;; 6. String but not a color format
      (string? color-value)
      (throw (js/Error. (str "Unsupported color string format: " color-value)))

      ;; 7. Other types
      :else
      (throw (js/Error. (str "Invalid color value type: " (type color-value) " - " color-value))))
    (catch js/Error e
      (js/console.error "Failed to convert color to hex:" e "Value:" color-value)
      (throw e))))

(defn get-opacity-value [opacity]
  "Get actual opacity value from MapLibre opacity object for current zoom level"
  (cond
    ;; 1. Simple number
    (number? opacity) opacity

    ;; 2. Stops format: {"stops": [[zoom1, value1], [zoom2, value2]]}
    (and (object? opacity) (.-stops opacity))
    (parse-maplibre-stops opacity)

    ;; 3. Expression format: ["interpolate", "linear", "zoom", z1, v1, z2, v2]
    (and (object? opacity) (.-expression opacity))
    (parse-maplibre-expression opacity)

    ;; 4. Other formats - throw error instead of returning default value
    (object? opacity) (throw (js/Error. (str "Unsupported opacity object format: " (js/JSON.stringify opacity))))

    :else (throw (js/Error. (str "Invalid opacity value type: " (type opacity))))))

(defn hex-to-rgba [hex-str opacity]
  "Convert hex string to rgba format for MapLibre - throw error on failure"
  (when hex-str
    (if (str/starts-with? hex-str "rgba")
      hex-str
      (let [opacity-value (get-opacity-value opacity)
            color-obj (color hex-str)
            rgb-obj (.rgb color-obj)
            rgba-obj (.alpha rgb-obj opacity-value)]
        (.string rgba-obj)))))

;; Current editing style state - use defonce to preserve state during hot reload
(defonce current-editing-style (reagent/atom (:light default-building-styles)))

(defn get-current-building-styles []
  "Get current building styles from the map instance - throw errors on failure"
  (js/console.log "=== DEBUG: Starting get-current-building-styles ===")
  (let [map-inst (.-pearlMapInstance js/window)
        building-layer-ids ["building" "building-top"]]
    (js/console.log "Map instance available:" (boolean map-inst))
    (when map-inst
      (js/console.log "Map loaded status:" (.-loaded map-inst))
      (let [styles (atom {})]
        ;; Check each building layer
        (doseq [layer-id building-layer-ids]
          (let [layer-exists (.getLayer map-inst layer-id)]
            (js/console.log "Layer" layer-id "exists:" layer-exists)
            (when layer-exists
              ;; Get layer's paint properties
              (doseq [style-key [:fill-color :fill-opacity :fill-outline-color]]
                (let [current-value (.getPaintProperty map-inst layer-id (name style-key))]
                  (js/console.log "Style" style-key "value:" current-value "type:" (type current-value))
                  (when current-value
                    ;; Process different types of values - throw errors on failure
                    (let [processed-value (cond
                                            (#{:fill-color :fill-outline-color} style-key)
                                            (rgba-to-hex current-value)

                                            (= style-key :fill-opacity)
                                            (get-opacity-value current-value)

                                            :else current-value)]
                      (js/console.log "Processed value:" processed-value)
                      (swap! styles assoc style-key processed-value))))))))
        (js/console.log "Final styles object:" @styles)
        @styles))))

(defn apply-current-style []
  "Apply the current editing style to the map buildings with proper error handling"
  (try
    (let [map-inst (.-pearlMapInstance js/window)
          style @current-editing-style]
      (when map-inst
        (if (.-loaded map-inst)
          ;; Validate style before applying
          (let [validation-result (validate-style style)]
            (if validation-result
              (let [building-layer-ids ["building" "building-top"]]
                (doseq [layer-id building-layer-ids]
                  (when (.getLayer map-inst layer-id)
                    (doseq [[style-key style-value] style]
                      (let [final-value (cond
                                          (#{:fill-color :fill-outline-color} style-key)
                                          (hex-to-rgba style-value (:fill-opacity style))

                                          (= style-key :fill-opacity)
                                          style-value

                                          :else style-value)]
                        (.setPaintProperty map-inst layer-id (name style-key) final-value))))))
              (do
                (js/console.error "Style validation failed - not applying changes")
                false)))
          (js/console.warn "Map is not loaded yet"))))
    (catch js/Error e
      (js/console.error "Failed to apply building style:" e)
      (throw e))))  ;; Re-throw error instead of failing silently

(defn update-building-style [style-key value]
  "Update building style and apply to map"
  ;; Check for empty values
  (when (and value (not (str/blank? value)))
    ;; Handle different types of values
    (let [processed-value (cond
                            (#{:fill-color :fill-outline-color} style-key)
                            (if (string? value)
                              (rgba-to-hex value)
                              (throw (js/Error. (str "Color value must be string, got: " (type value)))))

                            (= style-key :fill-opacity)
                            (if (number? value)
                              value
                              (js/parseFloat value))  ;; Try to parse if it's a string

                            :else value)]
      (swap! current-editing-style assoc style-key processed-value)
      (apply-current-style))))

;; Add a function to check if we should listen for map load events
(defn setup-map-listener []
  "Setup listener to automatically apply styles when map loads"
  (js/console.log "=== DEBUG: Setting up map listener ===")
  (let [map-inst (.-pearlMapInstance js/window)]
    (js/console.log "Map instance in setup:" (boolean map-inst))
    (when map-inst
      (js/console.log "Map already loaded:" (.-loaded map-inst))
      ;; Remove any existing listeners to avoid duplicates
      (.off map-inst "load")
      (.on map-inst "load"
           (fn []
             (js/console.log "Map loaded event received")
             ;; Get current styles and update editor state
             (when-let [current-styles (get-current-building-styles)]
               (js/console.log "Updating editor state with:" current-styles)
               (reset! current-editing-style current-styles))
             (apply-current-style))))))

(defn building-style-editor []
  "Building style editor component"
  ;; Setup map listener when component mounts
  (reagent/create-class
   {:component-did-mount
    (fn []
      (js/setTimeout setup-map-listener 1000)  ;; Delay to ensure map is initialized
      (js/setTimeout check-style-spec-on-load 3000))  ;; Check style-spec module
    :reagent-render
    (fn []
      [:div {:style {:position "absolute"
                     :top "100px"
                     :right "20px"
                     :z-index 1000
                     :background "rgba(255,255,255,0.95)"
                     :padding "15px"
                     :border-radius "8px"
                     :font-family "Arial, sans-serif"
                     :width "300px"
                     :box-shadow "0 2px 10px rgba(0,0,0,0.1)"}}
       [:h3 {:style {:margin "0 0 15px 0" :color "#333"}} "Building Style Editor"]

       [:div {:style {:margin-bottom "10px"}}
        [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Fill Color"]
        [:input {:type "color"
                 :value (or (:fill-color @current-editing-style) "#f0f0f0")
                 :on-change #(update-building-style :fill-color (-> % .-target .-value))
                 :style {:width "100%" :height "30px"}}]]

       [:div {:style {:margin-bottom "10px"}}
        [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Opacity"]
        [:input {:type "range"
                 :min "0" :max "1" :step "0.1"
                 :value (:fill-opacity @current-editing-style)
                 :on-change #(update-building-style :fill-opacity (js/parseFloat (-> % .-target .-value)))
                 :style {:width "100%"}}]
        [:span {:style {:font-size "12px"}} (str "Opacity: " (:fill-opacity @current-editing-style))]]

       [:div {:style {:margin-bottom "15px"}}
        [:label {:style {:display "block" :margin-bottom "5px" :font-weight "bold"}} "Outline Color"]
        [:input {:type "color"
                 :value (or (:fill-outline-color @current-editing-style) "#cccccc")
                 :on-change #(update-building-style :fill-outline-color (-> % .-target .-value))
                 :style {:width "100%" :height "30px"}}]]

       [:div {:style {:display "flex" :gap "10px" :margin-bottom "15px" :flex-wrap "wrap"}}
        [:button {:on-click #(do
                               (reset! current-editing-style (:light default-building-styles))
                               (apply-current-style))
                  :style {:padding "8px 12px" :border "none" :border-radius "4px"
                          :background "#007bff" :color "white" :cursor "pointer"}} "Light Theme"]
        [:button {:on-click #(do
                               (reset! current-editing-style (:dark default-building-styles))
                               (apply-current-style))
                  :style {:padding "8px 12px" :border "none" :border-radius "4px"
                          :background "#343a40" :color "white" :cursor "pointer"}} "Dark Theme"]
        ;; Add refresh current styles button
        [:button {:on-click #(when-let [current-styles (get-current-building-styles)]
                               (reset! current-editing-style current-styles)
                               ;; Force re-render by ensuring opacity is a number
                               (when-let [opacity (:fill-opacity current-styles)]
                                 (when (object? opacity)
                                   (swap! current-editing-style assoc :fill-opacity (get-opacity-value opacity)))))
                  :style {:padding "8px 12px" :border "none" :border-radius "4px"
                          :background "#28a745" :color "white" :cursor "pointer"}} "Refresh Styles"]]

       [:div {:style {:padding-top "15px" :border-top "1px solid #eee"}}
        [:p {:style {:color "#666" :font-size "12px" :margin "0 0 10px 0" :font-weight "bold"}}
         "Buildings Status:"]
        [:p {:style {:color "#666" :font-size "11px" :margin "0" :font-style "italic"}}
         "Only works with Dark or Light vector styles"]
        ;; Add current style status display
        [:p {:style {:color "#666" :font-size "11px" :margin "10px 0 0 0"}}
         "Current: " (pr-str (select-keys @current-editing-style [:fill-color :fill-opacity :fill-outline-color]))]]])}))
