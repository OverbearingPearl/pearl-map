(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [reagent.core :as reagent]
            [pearl-map.utils.geometry :as geom]
            [clojure.string :as str]
            ["maplibre-gl" :as maplibre]
            ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]
            ["three" :as three]))

;; --- Constants & Configuration ---

(def eiffel-tower-coords [2.2945 48.8584])

(def style-urls
  {:raster-style "raster-style"
   :dark-style "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
   :light-style "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"})


;; --- State Accessors ---

(defn get-map-instance []
  (:map-instance @db/app-db))

(defn get-custom-layers []
  (:custom-layers @db/app-db))

(defn get-current-zoom []
  (when-let [^js map-obj (get-map-instance)]
    (.getZoom map-obj)))


;; --- Map Initialization & Lifecycle ---

(defn set-map-instance! [instance]
  (re-frame/dispatch [:set-map-instance instance])
  (set! (.-pearlMapInstance js/window) instance))

(defn- create-config [style-url]
  (let [base-config {:container "map-container"
                     :center (clj->js eiffel-tower-coords)
                     :zoom 15
                     :pitch 45
                     :bearing 0
                     :attributionControl true
                     :maxZoom 19
                     :minZoom 0}]
    (if (= style-url "raster-style")
      (clj->js (assoc base-config
                      :style {:version 8
                              :name "OSM Bright"
                              :sources {:osm {:type "raster"
                                              :tiles ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"]
                                              :tileSize 256
                                              :attribution "© OpenStreetMap contributors"}}
                              :layers [{:id "osm-tiles"
                                        :type "raster"
                                        :source "osm"
                                        :minzoom 0
                                        :maxzoom 19}]}))
      (clj->js (assoc base-config :style style-url)))))

(defn init-map []
  (when (.getElementById js/document "map-container")
    (let [initial-style-key (:current-style-key @db/app-db)
          initial-style-url (get style-urls initial-style-key)
          map-config (create-config initial-style-url)
          map-obj (maplibre/Map. map-config)]
      (.on map-obj "styledataloading" (fn [_] (re-frame/dispatch [:set-map-loading? true])))
      (.on map-obj "styledata" (fn [_] (re-frame/dispatch [:set-map-loading? false])))
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
   {:dispatch-n (cons [:set-map-loading? false]
                      (mapv (fn [callback] [:map-engine/on-map-loaded callback map-obj])
                            (get db :map-engine/on-load-callbacks [])))
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

(defn- get-all-points [coords]
  (if (number? (first coords))
    [coords]
    (mapcat get-all-points coords)))

(defn- calculate-bounds-from-coords [coords]
  (let [points (get-all-points coords)]
    (when (seq points)
      (let [lngs (map first points)
            lats (map second points)]
        [[(apply min lngs) (apply min lats)]
         [(apply max lngs) (apply max lats)]]))))

(defn- fly-to-first-visible-feature [layer-id default-zoom]
  (when-let [^js map-obj (get-map-instance)]
    (let [features (.queryRenderedFeatures map-obj #js {:layers #js [layer-id]})]
      (when-let [feature (first features)]
        (let [geom (.-geometry feature)
              geom-type (.-type geom)
              coords (js->clj (.-coordinates geom))]
          (cond
            (= geom-type "Point")
            (.flyTo map-obj #js {:center (clj->js coords)
                                 :zoom default-zoom})

            (or (= geom-type "Polygon") (= geom-type "LineString")
                (= geom-type "MultiPolygon") (= geom-type "MultiLineString"))
            (when-let [bounds (calculate-bounds-from-coords coords)]
              (.fitBounds map-obj (clj->js bounds) #js {:padding 200 :maxZoom default-zoom}))

            :else nil))))))

(defn focus-on-layer [layer-id zoom-level]
  (when-let [^js map-obj (get-map-instance)]
    (let [current-center (.getCenter map-obj)]
      (.flyTo map-obj #js {:center current-center :zoom zoom-level})
      (.once map-obj "moveend"
             (fn []
               (fly-to-first-visible-feature layer-id zoom-level))))))


;; --- Style & Layer Management ---

(defn- get-current-map-state []
  (let [^js map-obj (get-map-instance)]
    (when map-obj
      {:center (.getCenter map-obj)
       :zoom (.getZoom map-obj)
       :pitch (.getPitch map-obj)
       :bearing (.getBearing map-obj)
       :layers (get-custom-layers)})))

(defn- apply-map-state! [^js map-obj state]
  (when map-obj
    (doto map-obj
      (.setCenter (:center state))
      (.setZoom (:zoom state))
      (.setPitch (:pitch state))
      (.setBearing (:bearing state)))))

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
   (when-not (.getLayer map-obj layer-id)
     (.addLayer map-obj layer-impl before-id)
     (register-custom-layer layer-id layer-impl))))

(defn- reapply-custom-layers! [^js map-obj layers]
  (clear-custom-layers map-obj)
  (doseq [[layer-id layer-impl] layers]
    (add-custom-layer map-obj layer-id layer-impl nil)))

(defn change-map-style [style-url]
  (let [^js map-obj (get-map-instance)
        current-state (get-current-map-state)
        style-key (->> style-urls
                       (filter (fn [[_ v]] (= v style-url)))
                       ffirst)]
    (re-frame/dispatch-sync [:set-map-loading? true])
    (when map-obj (.remove map-obj))
    (re-frame/dispatch-sync [:set-current-style-key style-key])
    (re-frame/dispatch-sync [:set-map-instance nil])
    (set! (.-pearlMapInstance js/window) nil)
    (re-frame/dispatch-sync [:clear-custom-layers])
    (reagent/next-tick
     (fn []
       (init-map)
       (on-map-load
        (fn [^maplibre/Map new-map-obj]
          (apply-map-state! new-map-obj current-state)
          (reapply-custom-layers! new-map-obj (:layers current-state))
          (when (not= style-url "raster-style")
            (re-frame/dispatch [:buildings/add-layers]))
          (.once new-map-obj "idle"
                 (fn []
                   (re-frame/dispatch [:style-editor/reset-to-defaults])))))))))

(defn remove-custom-layer [layer-id]
  (let [^js map-obj (get-map-instance)]
    (.removeLayer map-obj layer-id)
    (unregister-custom-layer layer-id)))

(defn layer-exists? [layer-id]
  (when-let [^js map-obj (get-map-instance)]
    (.getLayer map-obj layer-id)))

(defn get-paint-property [layer-id property-name]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (try
        (.getPaintProperty map-obj layer-id property-name)
        (catch js/Error e
          ;; This can happen if property is not applicable for layer type.
          ;; MapLibre throws a TypeError in this case.
          ;; We can safely ignore it and return nil.
          nil)))))

(defn get-layout-property [layer-id property-name]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (try
        (.getLayoutProperty map-obj layer-id property-name)
        (catch js/Error e
          nil)))))

(defn- clj-expression? [x]
  (or (and (vector? x) (string? (first x)) (> (count x) 1))
      (and (map? x) (or (:stops x) (:property x) (:type x)))))

(defn set-paint-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (let [visibility (.getLayoutProperty map-obj layer-id "visibility")]
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
                           :else processed-value)
                layer-type (.-type (.getLayer map-obj layer-id))]
            (when (or (not= "fill-extrusion-color" property-name)
                      (= "fill-extrusion" layer-type))
              (.setPaintProperty map-obj layer-id property-name js-value))))))))

(defn set-layout-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (let [processed-value (if (and (string? value) (str/blank? value)) nil value)
            js-value (clj->js processed-value)]
        (try
          (.setLayoutProperty map-obj layer-id property-name js-value)
          (catch js/Error e
            (js/console.error (str "map-engine/set-layout-property: Error calling .setLayoutProperty for " layer-id " " property-name) e)))))))


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
                      t (/ (- zoom prev-zoom) (- current-zoom prev-zoom))
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
          (mapv (fn [[zoom prop-value]]
                  {:zoom zoom
                   :value (parse-fn prop-value current-zoom)})
                (:stops clj-value))

          ;; array-style interpolate on zoom
          (and (vector? clj-value)
               (>= (count clj-value) 4)
               (= "interpolate" (first clj-value))
               (= ["zoom"] (get clj-value 2)))
          (let [stops-pairs (partition 2 (subvec clj-value 3))]
            (mapv (fn [[zoom prop-value]]
                    {:zoom zoom
                     :value (parse-fn prop-value current-zoom)})
                  stops-pairs))

          :else
          [{:zoom current-zoom
            :value (parse-fn value current-zoom)}])))))

(defn update-single-value [layer-id property-name new-value]
  "Update a property to a single value (not a stops expression)"
  new-value)

(defn update-zoom-value-pair [layer-id property-name zoom new-value prop-type]
  (let [current-value (if (= prop-type "layout")
                        (get-layout-property layer-id property-name)
                        (get-paint-property layer-id property-name))
        current-map-zoom (get-current-zoom)
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
                         stop-exists? (some #(= zoom (first %)) stops)
                         updated-stops-pairs (if stop-exists?
                                               (mapv (fn [[stop-zoom stop-value]]
                                                       (if (= stop-zoom zoom)
                                                         [stop-zoom new-value]
                                                         [stop-zoom stop-value]))
                                                     stops)
                                               (sort-by first (conj stops [zoom new-value])))
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
                         stop-exists? (some #(= zoom (first %)) stops-pairs)
                         updated-stops-pairs (if stop-exists?
                                               (mapv (fn [[stop-zoom stop-value]]
                                                       (if (= stop-zoom zoom)
                                                         [stop-zoom new-value]
                                                         [stop-zoom stop-value]))
                                                     stops-pairs)
                                               (sort-by first (conj stops-pairs [zoom new-value])))
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
