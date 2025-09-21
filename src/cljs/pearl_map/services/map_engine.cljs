(ns pearl-map.services.map-engine
  (:require [re-frame.core :as re-frame]
            ["maplibre-gl" :as maplibre]
            ["color" :as color]
            ["@maplibre/maplibre-gl-style-spec" :as style-spec]))

;; Eiffel Tower coordinates for Paris focus [longitude, latitude]
(def eiffel-tower-coords [2.2945 48.8584])

;; Style URLs configuration
(def style-urls
  {:basic "raster-style"
   :dark "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
   :light "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"})

;; Map instance management
(defonce map-instance (atom nil))

(defn get-map-instance []
  @map-instance)

(defn set-map-instance! [instance]
  (reset! map-instance instance)
  (set! (.-pearlMapInstance js/window) instance))

;; Map initialization
(defn create-raster-config []
  (clj->js {:container "map-container"
            :style {:version 8
                    :name "OSM Bright"
                    :center (clj->js eiffel-tower-coords)
                    :zoom 15
                    :pitch 45
                    :bearing 0
                    :sources {:osm {:type "raster"
                                    :tiles ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"]
                                    :tileSize 256
                                    :attribution "© OpenStreetMap contributors"}}
                    :layers [{:id "osm-tiles"
                              :type "raster"
                              :source "osm"
                              :minzoom 0
                              :maxzoom 19}]}
            :attributionControl true
            :maxZoom 19
            :minZoom 0}))

(defn create-vector-config [style-url]
  (clj->js {:container "map-container"
            :style style-url
            :center (clj->js eiffel-tower-coords)
            :zoom 15
            :pitch 45
            :bearing 0
            :attributionControl true
            :maxZoom 19
            :minZoom 0}))

(defn init-map []
  (let [current-style "raster-style"
        map-element (.getElementById js/document "map-container")]
    (when map-element
      (js/console.log "Initializing map with style:" current-style)
      (try
        (let [map-config (if (= current-style "raster-style")
                           (create-raster-config)
                           (create-vector-config current-style))
              map-obj (maplibre/Map. map-config)]

          (set-map-instance! map-obj)
          (re-frame/dispatch [:set-map-instance map-obj])

          ;; Add controls
          (.addControl map-obj (maplibre/NavigationControl.))
          (.addControl map-obj (maplibre/ScaleControl.))

          map-obj)
        (catch js/Error e
          (js/console.error "Failed to initialize map:" e)
          nil)))))

;; Event handlers
(defn on-map-load [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "load" #(callback map-obj))))

(defn on-map-error [callback]
  (when-let [^js map-obj (get-map-instance)]
    (.on map-obj "error" #(callback %))))

;; Layer management
(defn add-buildings-layer []
  (when-let [^js map-obj (get-map-instance)]
    (when (and map-obj (not= (.getStyle map-obj) "raster-style"))
      (try
        ;; Wait for the style to be fully loaded
        (.once map-obj "idle"
               (fn []
                 ;; Check if the composite source exists before adding the layer
                 (let [sources (js->clj (.getSources map-obj))]
                   (if (get sources "composite")
                     (try
                       (when (not (.getLayer map-obj "buildings"))
                         ;; Add the buildings layer
                         (.addLayer map-obj
                                    (clj->js
                                     {:id "buildings"
                                      :type "fill"
                                      :source "composite"
                                      :source-layer "building"
                                      :filter ["==" "extrude" "true"]
                                      :paint {:fill-color "#f0f0f0"
                                              :fill-opacity 0.7
                                              :fill-outline-color "#cccccc"}}))
                         (js/console.log "Buildings layer added successfully"))
                       (catch js/Error e
                         (js/console.warn "Could not add buildings layer (source-layer may not exist):" e)))
                     (js/console.warn "Composite source not found - cannot add buildings layer")))))
        true
        (catch js/Error e
          (js/console.warn "Could not add buildings layer:" e)
          false)))))

;; Style management
(defn change-map-style [style-url]
  (when-let [^js map-obj (get-map-instance)]
    (try
      (if (= style-url "raster-style")
        (do
          (.remove map-obj)
          (set-map-instance! nil)
          (re-frame/dispatch [:set-map-instance nil])
          (js/setTimeout init-map 100)
          true)
        (do
          ;; Set the new style
          (.setStyle map-obj style-url)
          ;; Add buildings layer after the new style is loaded, only if it's not a raster style
          (.once map-obj "idle"
                 (fn []
                   ;; Check if the style is not raster before trying to add buildings layer
                   (when (not= (.getStyle map-obj) "raster-style")
                     (add-buildings-layer))))
          true))
      (catch js/Error e
        (js/console.error "Failed to change style:" e)
        false))))

;; Style property management
(defn get-paint-property [layer-id property-name]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (.getPaintProperty map-obj layer-id property-name))))

(defn set-paint-property [layer-id property-name value]
  (when-let [^js map-obj (get-map-instance)]
    (when (.getLayer map-obj layer-id)
      (.setPaintProperty map-obj layer-id property-name value))))

;; Utility functions
(defn get-current-zoom []
  (when-let [^js map-obj (get-map-instance)]
    (.getZoom map-obj)))

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

;; Color conversion utilities
(defn rgba-to-hex [color-value]
  (try
    (cond
      (and (string? color-value) (.startsWith color-value "#"))
      color-value

      (and (string? color-value) (or (.includes color-value "rgba")
                                     (.includes color-value "rgb")))
      (-> (color color-value)
          (.hex)
          (.toString)
          (.toLowerCase))

      :else
      (throw (js/Error. (str "Unsupported color format: " color-value))))
    (catch js/Error e
      (js/console.error "Failed to convert color to hex:" e)
      (throw e))))

(defn hex-to-rgba [hex-str opacity]
  (when hex-str
    (if (.startsWith hex-str "rgba")
      hex-str
      (let [color-obj (color hex-str)
            rgb-obj (.rgb color-obj)
            rgba-obj (.alpha rgb-obj opacity)]
        (.string rgba-obj)))))

;; Style validation
(defn validate-style [style]
  (try
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

      (if (and (array? validation-result) (== (.-length validation-result) 0))
        true
        (do
          (js/console.error "Validation errors:" validation-result)
          false)))
    (catch js/Error e
      (js/console.error "Style validation failed:" e)
      false)))
