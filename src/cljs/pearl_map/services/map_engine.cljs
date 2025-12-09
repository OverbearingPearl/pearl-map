(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.config :as config]
            [clojure.string :as str]
            ["maplibre-gl" :as maplibre]
            ["color" :as color] ; For color manipulation in expressions
            [goog.object :as gobj] ; For accessing window.devicePixelRatio
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]
            ["three" :as three]))

;; --- Constants & Configuration ---



;; --- State Accessors ---

(defn get-map-instance []
  (:map-instance @db/app-db))

(defn get-custom-layers []
  (:custom-layers @db/app-db))

(defn get-layer-min-zoom [layer-id]
  (when-let [^js map-obj (get-map-instance)]
    (when-let [layer (.getLayer map-obj layer-id)]
      (let [min-zoom (.-minzoom layer)]
        (when (number? min-zoom)
          min-zoom)))))

(defn get-layer-max-zoom [layer-id]
  (when-let [^js map-obj (get-map-instance)]
    (when-let [layer (.getLayer map-obj layer-id)]
      (let [max-zoom (.-maxzoom layer)]
        (when (number? max-zoom)
          max-zoom)))))


;; --- Layer Management (Moved up for initialization access) ---

(defn- clear-custom-layers [^js map-obj]
  (let [layers (get-custom-layers)]
    (doseq [[layer-id _] layers]
      (when (.getLayer map-obj layer-id)
        (.removeLayer map-obj layer-id)))
    (re-frame/dispatch [:clear-custom-layers])))

(defn register-custom-layer [layer-id layer-impl]
  (re-frame/dispatch [:register-custom-layer layer-id layer-impl]))

(defn unregister-custom-layer [layer-id]
  (re-frame/dispatch [:unregister-custom-layer layer-id]))

(defn add-custom-layer
  ([layer-id layer-impl before-id]
   (add-custom-layer (get-map-instance) layer-id layer-impl before-id))
  ([^js map-obj layer-id layer-impl before-id]
   ;; Always register the layer in DB so it persists/restores on map reload
   (register-custom-layer layer-id layer-impl)
   ;; Only attempt to add to map if map instance exists
   (when map-obj
     (when-not (.getLayer map-obj layer-id)
       (.addLayer map-obj layer-impl before-id)))))

(defn- reapply-custom-layers! [^js map-obj layers]
  (clear-custom-layers map-obj)
  (doseq [[layer-id layer-impl] layers]
    (add-custom-layer map-obj layer-id layer-impl nil)))

(defn remove-custom-layer [layer-id]
  (let [^js map-obj (get-map-instance)]
    (.removeLayer map-obj layer-id)
    (unregister-custom-layer layer-id)))


;; --- Map Initialization & Lifecycle ---

(defn set-map-instance! [instance]
  (re-frame/dispatch [:set-map-instance instance])
  (set! (.-pearlMapInstance js/window) instance))

(defn- get-max-parallel-requests
  "Determines the maximum number of parallel image requests based on the user agent.
   Firefox has a lower limit (8) compared to other browsers (16) to prevent issues."
  []
  (if (str/includes? (str (gobj/get js/window "navigator" "userAgent")) "Firefox")
    8
    16))

(defn- get-max-tile-cache-size
  "Calculates the maximum tile cache size based on the device pixel ratio (DPR).
   A higher DPR suggests a higher resolution screen, potentially benefiting from a larger cache."
  []
  (let [dpr (or (gobj/get js/window "devicePixelRatio") 1)
        cache-size (js/Math.round (* 1024 dpr))]
    (js/console.log (str "Calculated maxTileCacheSize: " cache-size " (based on DPR: " dpr ")"))
    cache-size)) ; Base cache size (1024) scaled by DPR

(defn- transform-request-for-cache
  "Applies force-cache to font requests to ensure they are cached by the browser."
  [url resource-type]
  (if (= resource-type "Glyphs")
    #js {:url url :cache "force-cache"}
    #js {:url url}))

(defn- create-config
  "Creates the MapLibre GL JS configuration object."
  [style-url]
  (let [base-config {:container "map-container"
                     :center (clj->js config/eiffel-tower-coords)
                     :zoom 15
                     :pitch 45
                     :bearing 0
                     :attributionControl true
                     :maxZoom 19
                     :minZoom 0
                     :maxParallelImageRequests (get-max-parallel-requests)
                     :maxTileCacheSize (get-max-tile-cache-size)
                     :transformRequest transform-request-for-cache}]
    (if (= style-url "raster-style")
      (clj->js (assoc base-config :style config/raster-style-object))
      (clj->js (assoc base-config :style style-url)))))

(defn init-map []
  (when (.getElementById js/document "map-container")
    ;; Cleanup existing map instance to prevent race conditions during hot-reload/remount
    (when-let [old-map (get-map-instance)]
      (try
        (.remove old-map)
        (catch js/Error e
          (js/console.warn "Error removing old map instance:" e)))
      ;; Synchronously clear the instance so subsequent calls to on-map-load
      ;; know to wait for the new map.
      (re-frame/dispatch-sync [:set-map-instance nil]))

    (let [initial-style-key (:current-style-key @db/app-db)
          initial-style-url (get config/style-urls initial-style-key)
          map-config (create-config initial-style-url)
          map-obj (maplibre/Map. map-config)]
      (.on map-obj "styledataloading" (fn [_] (re-frame/dispatch [:set-map-loading? true])))
      (.on map-obj "styledata" (fn [_] (re-frame/dispatch [:set-map-loading? false])))
      (.on map-obj "move" (fn [] (re-frame/dispatch [:map/set-zoom (.getZoom map-obj)])))
      (.once map-obj "load"
             (fn []
               (re-frame/dispatch [:map-engine/map-loaded map-obj])))
      (doto map-obj
        (.addControl (maplibre/NavigationControl.))
        (.addControl (maplibre/ScaleControl.))))))

(re-frame/reg-event-fx
 :map-engine/map-loaded
 (fn [{:keys [db]} [_ map-obj]]
   (set-map-instance! map-obj)

   ;; Restore custom layers if any exist in DB (e.g. from hot reload)
   (let [custom-layers (:custom-layers db)]
     (when (seq custom-layers)
       (reapply-custom-layers! map-obj custom-layers)))

   {:dispatch-n (cond-> (cons [:set-map-loading? false]
                              (mapv (fn [callback] [:map-engine/on-map-loaded callback map-obj])
                                    (get db :map-engine/on-load-callbacks [])))
                  ;; Ensure building layers are added if not in raster mode
                  (not= (:current-style-key db) :raster-style)
                  (conj [:buildings/add-layers]))
    :db         (assoc db :map-engine/on-load-callbacks [])}))

(re-frame/reg-event-fx
 :map-engine/on-map-loaded
 (fn [_ [_ callback map-obj]]
   (callback map-obj)
   {}))

(re-frame/reg-event-db
 :map-engine/register-on-load-callback
 (fn [db [_ callback]]
   (update db :map-engine/on-load-callbacks conj callback)))

(defn on-map-load [callback]
  (if-let [^js map-obj (get-map-instance)]
    (if (.-loaded map-obj)
      (re-frame/dispatch [:map-engine/on-map-loaded callback map-obj])
      (re-frame/dispatch [:map-engine/register-on-load-callback callback]))
    (re-frame/dispatch [:map-engine/register-on-load-callback callback])))

(defn on-map-error [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "error" #(callback %))))


;; --- Map View Control ---

(defn set-center [coords]
  (when-let [^js map-obj (get-map-instance)]
    (.setCenter map-obj (clj->js coords))))

(defn set-zoom [zoom-level]
  (when-let [^js map-obj (get-map-instance)]
    (.setZoom map-obj zoom-level)))

(defn set-pitch [pitch-angle]
  (when-let [^js map-obj (get-map-instance)]
    (.setPitch map-obj pitch-angle)))

(defn set-bearing [bearing-angle]
  (when-let [^js map-obj (get-map-instance)]
    (.setBearing map-obj bearing-angle)))

(defn fly-to-location [coords zoom]
  (when-let [^js map-obj (get-map-instance)]
    (.flyTo map-obj #js {:center (clj->js coords)
                         :zoom zoom
                         :pitch 45
                         :bearing 0
                         :speed 0.6
                         :curve 1.5
                         :essential true})))

(defn zoom-to-level [zoom callback]
  (when-let [^js map-obj (get-map-instance)]
    (.flyTo map-obj #js {:zoom zoom :speed 0.8 :essential true})
    (when callback
      (.once map-obj "moveend" callback))))

(defn- get-all-points [coords]
  (if (number? (first coords))
    [coords]
    (mapcat get-all-points coords)))

(defn- dist-sq [p1 p2]
  (let [dx (- (.-x p1) (.-x p2))
        dy (- (.-y p1) (.-y p2))]
    (+ (* dx dx) (* dy dy))))

(defn find-next-visible-feature [layer-id excluded-coords-set]
  (when-let [^js map-obj (get-map-instance)]
    (let [features (.queryRenderedFeatures map-obj #js {:layers #js [layer-id]})]
      (when (seq features)
        (let [center-point (.project map-obj (.getCenter map-obj))
              ;; Helper to check if a coordinate is effectively in the excluded set
              is-excluded? (fn [coords] (contains? excluded-coords-set coords))

              calc-dist-sq (fn [feature]
                             (let [coords (-> feature .-geometry .-coordinates js->clj)
                                   flat-coords (get-all-points coords)
                                   target-coord (first flat-coords)]
                               (if (and target-coord (not (is-excluded? target-coord)))
                                 (let [point (.project map-obj (maplibre/LngLat. (first target-coord) (second target-coord)))
                                       d (dist-sq point center-point)]
                                   ;; Filter out points that are extremely close to center (current position)
                                   ;; 100 pixels squared = 10 pixels distance threshold
                                   (if (> d 100)
                                     d
                                     js/Infinity))
                                 js/Infinity)))

              ;; Find feature with minimum distance that is valid
              candidates (map (fn [f]
                                {:feature f
                                 :dist (calc-dist-sq f)})
                              features)
              valid-candidates (filter #(not= (:dist %) js/Infinity) candidates)]

          (when (seq valid-candidates)
            (let [best (apply min-key :dist valid-candidates)
                  feature (:feature best)]
              (-> feature .-geometry .-coordinates js->clj get-all-points first))))))))



;; --- Style & Layer Management ---

(defn- get-current-map-state []
  (let [^js map-obj (get-map-instance)]
    (when map-obj
      {:center (.getCenter map-obj)
       :zoom (.getZoom map-obj)
       :pitch (.getPitch map-obj)
       :bearing (.getBearing map-obj)
       :layers (get-custom-layers)})))

(defn- prewarm-step [^js map-obj center zooms-to-warm original-camera-options on-complete]
  (if (empty? zooms-to-warm)
    (on-complete)
    (let [zoom (first zooms-to-warm)
          remaining-zooms (rest zooms-to-warm)]
      (js/console.log (str "Warming zoom level: " zoom))
      (.jumpTo map-obj #js {:center (clj->js center) :zoom zoom}) ; Jump to the current zoom level
      (.once map-obj "idle"
             (fn [] ; Once map is idle (rendered), proceed to the next zoom level
               (prewarm-step map-obj center remaining-zooms original-camera-options on-complete))))))

(defn prewarm-tiles [center on-complete-callback]
  (when-let [^js map-obj (get-map-instance)]
    (let [container (.getContainer map-obj)
          original-visibility (.. container -style -visibility)
          original-camera-options (get-current-map-state)
          min-zoom (.getMinZoom map-obj)
          max-zoom (.getMaxZoom map-obj)
          zooms-to-warm (range (js/Math.ceil min-zoom) (inc (js/Math.floor max-zoom)))
          on-complete (fn []
                        (let [opts #js{}]
                          (aset opts "center" (:center original-camera-options))
                          (aset opts "zoom" (:zoom original-camera-options))
                          (aset opts "pitch" (:pitch original-camera-options))
                          (aset opts "bearing" (:bearing original-camera-options))
                          (.jumpTo map-obj opts))
                        (set! (.. container -style -visibility) original-visibility)
                        (.triggerRepaint map-obj)
                        (js/console.log "Prewarming complete. Restored original camera.")
                        (when on-complete-callback (on-complete-callback)))]
      (js/console.log (str "Prewarming tiles for center " center " from zoom " min-zoom " to " max-zoom))
      (set! (.. container -style -visibility) "hidden")
      (prewarm-step map-obj center zooms-to-warm original-camera-options on-complete))))

(defn- apply-map-state! [^js map-obj state]
  (when map-obj
    (doto map-obj
      (.setCenter (:center state))
      (.setZoom (:zoom state))
      (.setPitch (:pitch state))
      (.setBearing (:bearing state)))))

(defn change-map-style [style-url]
  (when-let [^js map-obj (get-map-instance)]
    (let [style-key (->> config/style-urls
                         (filter (fn [[_ v]] (= v style-url)))
                         ffirst)
          custom-layers (get-custom-layers)
          new-style (if (= style-url "raster-style")
                      (clj->js config/raster-style-object)
                      style-url)]
      (re-frame/dispatch-sync [:set-map-loading? true])
      (re-frame/dispatch-sync [:set-current-style-key style-key])

      (.setStyle map-obj new-style)

      (.once map-obj "styledata"
             (fn []
               (reapply-custom-layers! map-obj custom-layers)
               (when (not= style-url "raster-style")
                 (re-frame/dispatch [:buildings/add-layers]))
               (re-frame/dispatch [:style-editor/reset-to-defaults]))))))

(defn layer-exists? [layer-id]
  (when-let [^js map-obj (get-map-instance)]
    (.getLayer map-obj layer-id)))

(defn get-paint-property [layer-id property-name]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (.getPaintProperty map-obj layer-id property-name))))

(defn get-layout-property [layer-id property-name]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (.getLayoutProperty map-obj layer-id property-name))))

(defn- clj-expression? [x]
  (or (and (vector? x) (string? (first x)) (> (count x) 1))
      (and (map? x) (or (:stops x) (:property x) (:type x)))))

(defn set-paint-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when-let [layer (.getLayer map-obj layer-id)]
      (let [visibility (.getLayoutProperty map-obj layer-id "visibility")
            layer-type (.-type layer)]
        (when (not= visibility "none")
          (let [processed-value (if (and (string? value) (str/blank? value)) nil value)
                js-value (cond
                           (nil? processed-value) nil
                           (clj-expression? processed-value) (clj->js processed-value)
                           (#{"fill-extrusion-color" "fill-color" "fill-outline-color" "line-color" "text-color" "background-color"} property-name)
                           (cond
                             (= processed-value "transparent") "transparent"
                             (string? processed-value) (if (or (.startsWith processed-value "#")
                                                               (.startsWith processed-value "rgb")
                                                               (.startsWith processed-value "hsl"))
                                                         processed-value
                                                         processed-value)
                             (number? processed-value) (str "#" (.toString (js/parseInt (str processed-value)) 16))
                             :else processed-value)
                           :else processed-value)]
            (when (or (not= "fill-extrusion-color" property-name)
                      (= "fill-extrusion" layer-type))
              (.setPaintProperty map-obj layer-id property-name js-value))))))))

(defn set-layout-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (let [processed-value (if (and (string? value) (str/blank? value)) nil value)
            js-value (clj->js processed-value)]
        (.setLayoutProperty map-obj layer-id property-name js-value)))))


;; --- Style Expression Handling ---

(defn expression? [x]
  (cond
    (array? x) (and (string? (first x)) (> (count x) 1))
    (object? x) (or (some? (.-stops x))
                    (some? (.-property x))
                    (some? (.-type x)))
    :else false))

(defn- interpolate-stops [stops zoom base]
  (let [stop-count (count stops)]
    (when-not (zero? stop-count)
      (loop [i 0]
        (cond
          (>= i stop-count)
          (second (nth stops (dec stop-count)))

          :else
          (let [[current-zoom current-value] (nth stops i)]
            (if (<= zoom current-zoom)
              (if (zero? i)
                current-value
                (let [[prev-zoom prev-value] (nth stops (dec i))
                      t (/ (- zoom prev-zoom) (- current-zoom prev-value))
                      interpolated-t (if (= base 1)
                                       t
                                       (/ (- (js/Math.pow base t) 1)
                                          (- base 1)))]
                  (cond
                    (and (number? prev-value) (number? current-value))
                    (+ prev-value (* (- current-value prev-value) interpolated-t))

                    (and (string? prev-value) (string? current-value))
                    (-> (color prev-value) (.mix (color current-value) interpolated-t) .rgb)

                    :else
                    current-value)))
              (recur (inc i)))))))))

(defn- evaluate [expr properties]
  (cond
    ;; array-style interpolate on zoom
    (and (array? expr)
         (>= (count expr) 3)
         (= "interpolate" (aget expr 0))
         (array? (aget expr 2))
         (= "zoom" (aget (aget expr 2) 0)))
    (let [clj-expr (js->clj expr)
          stops-data (subvec clj-expr 3)
          stops (partition 2 stops-data)
          zoom (.-zoom properties)
          base 1] ;; NOTE: Assuming linear interpolation for now
      (interpolate-stops stops zoom base))

    (and (.-stops expr) (.-zoom properties))
    (let [stops (vec (js->clj (.-stops expr)))
          zoom (.-zoom properties)
          base (or (.-base expr) 1)]
      (interpolate-stops stops zoom base))

    (.-value expr) (.-value expr)
    (.-default expr) (.-default expr)

    :else expr))

(defn parse-color-expression [color-value current-zoom]
  (when color-value
    (cond
      (string? color-value)
      (cond
        (= color-value "transparent") "transparent"
        (.startsWith color-value "#") color-value
        :else (-> (color color-value)
                  (.hex)
                  (.toString)
                  (.toLowerCase)))

      (expression? color-value)
      (let [result (evaluate color-value #js {:zoom current-zoom})]
        (cond
          (string? result)
          result

          (and (object? result) (fn? (.-toString result)))
          (.toString result)

          :else
          (throw (js/Error. (str "Expression evaluated to non-string color: " result)))))

      (number? color-value)
      (str "#" (.toString (js/Math.floor color-value) 16))

      (object? color-value)
      (let [value (or (.-default color-value)
                      (.-value color-value)
                      (.-valueOf color-value))]
        (parse-color-expression value current-zoom))

      :else
      (throw (js/Error. (str "Unexpected color value type: " (type color-value)))))))

(defn parse-numeric-expression [numeric-value current-zoom]
  (cond
    (nil? numeric-value) nil
    (number? numeric-value) numeric-value

    (expression? numeric-value)
    (let [result (evaluate numeric-value #js {:zoom current-zoom})]
      (cond
        (number? result) result
        (string? result) (let [parsed (js/parseFloat result)]
                           (when-not (js/isNaN parsed) parsed))
        :else (throw (js/Error. (str "Expression evaluated to non-numeric value: " result)))))

    (string? numeric-value)
    (let [parsed (js/parseFloat numeric-value)]
      (when-not (js/isNaN parsed) parsed))

    (object? numeric-value)
    (let [value (or (.-default numeric-value)
                    (.-value numeric-value)
                    (.-valueOf numeric-value))]
      (cond
        (number? value) value
        (string? value) (let [parsed (js/parseFloat value)]
                          (when-not (js/isNaN parsed) parsed))
        :else nil))

    :else nil))

(defn get-zoom-value-pairs [layer-id property-name current-zoom prop-type]
  (let [value (if (= prop-type "layout")
                (get-layout-property layer-id property-name)
                (get-paint-property layer-id property-name))]
    (when value
      (let [clj-value (js->clj value :keywordize-keys true)
            parse-fn (if (#{"fill-color" "fill-outline-color" "fill-extrusion-color" "line-color" "text-color" "background-color"} property-name)
                       parse-color-expression
                       parse-numeric-expression)]
        (cond
          ;; object-style stops
          (and (map? clj-value) (:stops clj-value))
          (->> (:stops clj-value)
               (mapv (fn [[zoom prop-value]]
                       {:zoom zoom
                        :value (parse-fn prop-value current-zoom)}))
               (sort-by :zoom >))

          ;; array-style interpolate on zoom
          (and (vector? clj-value)
               (>= (count clj-value) 4)
               (= "interpolate" (first clj-value))
               (= ["zoom"] (get clj-value 2)))
          (let [stops-pairs (partition 2 (subvec clj-value 3))]
            (->> stops-pairs
                 (mapv (fn [[zoom prop-value]]
                         {:zoom zoom
                          :value (parse-fn prop-value current-zoom)}))
                 (sort-by :zoom >)))

          :else
          [{:zoom current-zoom
            :value (parse-fn value current-zoom)}])))))

(defn update-single-value [layer-id property-name new-value]
  "Update a property to a single value (not a stops expression)"
  new-value)

(defn update-zoom-value-pair [layer-id property-name zoom-raw new-value prop-type]
  (let [zoom (js/parseFloat zoom-raw) ;; Ensure zoom is a number
        current-value (if (= prop-type "layout")
                        (get-layout-property layer-id property-name)
                        (get-paint-property layer-id property-name))
        current-map-zoom (js/parseFloat (:map/zoom @db/app-db)) ;; Ensure map zoom is a number
        clj-value (if (some? current-value) (js->clj current-value :keywordize-keys true) nil)]
    (let [result (cond
                   ;; If current-value is not an expression, create one if the zoom levels differ
                   (not (expression? current-value))
                   (if (and (not= zoom current-map-zoom) (some? current-value))
                     (let [expression ["interpolate" ["linear"] ["zoom"] current-map-zoom current-value zoom new-value]]
                       (if (= prop-type "layout")
                         (set-layout-property layer-id property-name expression)
                         (set-paint-property layer-id property-name expression))
                       expression)
                     (do
                       (if (= prop-type "layout")
                         (set-layout-property layer-id property-name new-value)
                         (set-paint-property layer-id property-name new-value))
                       new-value))

                   ;; object-style stops (e.g., {:stops [[z1 v1] [z2 v2]]})
                   (and (map? clj-value) (:stops clj-value))
                   (let [stops (vec (:stops clj-value))
                         ;; Ensure we compare numbers
                         stop-exists? (some #(= (js/parseFloat (first %)) zoom) stops)
                         updated-stops-pairs (if stop-exists?
                                               (mapv (fn [[stop-zoom stop-value]]
                                                       (if (= (js/parseFloat stop-zoom) zoom)
                                                         [zoom new-value]
                                                         [(js/parseFloat stop-zoom) stop-value]))
                                                     stops)
                                               (sort-by first > (conj (mapv (fn [[z v]] [(js/parseFloat z) v]) stops)
                                                                      [zoom new-value])))
                         ;; Convert to new expression format
                         header ["interpolate" ["linear"] ["zoom"]]
                         updated-stops (reduce into [] updated-stops-pairs)
                         updated-expression (vec (concat header updated-stops))]
                     (if (= prop-type "layout")
                       (set-layout-property layer-id property-name updated-expression)
                       (set-paint-property layer-id property-name updated-expression))
                     updated-expression)

                   ;; array-style interpolate on zoom (e.g., ["interpolate", ["linear"], ["zoom"], z1, v1, z2, v2])
                   (and (vector? clj-value)
                        (>= (count clj-value) 4)
                        (= "interpolate" (first clj-value))
                        (= ["zoom"] (get clj-value 2)))
                   (let [header (subvec clj-value 0 3)
                         stops-data (subvec clj-value 3)
                         stops-pairs (partition 2 stops-data)
                         stop-exists? (some #(= (js/parseFloat (first %)) zoom) stops-pairs)
                         updated-stops-pairs (if stop-exists?
                                               (mapv (fn [[stop-zoom stop-value]]
                                                       (if (= (js/parseFloat stop-zoom) zoom)
                                                         [zoom new-value]
                                                         [(js/parseFloat stop-zoom) stop-value]))
                                                     stops-pairs)
                                               (sort-by first > (conj (mapv (fn [[z v]] [(js/parseFloat z) v]) stops-pairs)
                                                                      [zoom new-value])))
                         updated-stops (reduce into [] updated-stops-pairs)
                         updated-expression (vec (concat header updated-stops))]
                     (if (= prop-type "layout")
                       (set-layout-property layer-id property-name updated-expression)
                       (set-paint-property layer-id property-name updated-expression))
                     updated-expression)

                   ;; Default case: Fallback to setting the new value as a constant.
                   :else
                   (do
                     (if (= prop-type "layout")
                       (set-layout-property layer-id property-name new-value)
                       (set-paint-property layer-id property-name new-value))
                     new-value))]
      result)))


;; --- Validation ---

(defn validate-style [style]
  (and (map? style)
       (every? (fn [[k v]]
                 (and (keyword? k)
                      (or (string? v)
                          (number? v)
                          (nil? v)
                          (expression? v)
                          (and (map? v)
                               (or (:stops v)
                                   (:property v)
                                   (:type v))))))
               style)))
