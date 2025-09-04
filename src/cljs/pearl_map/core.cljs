(ns pearl-map.core
  (:require [reagent.core :as reagent]
            [reagent.dom.client :as rdomc]
            ;; Import maplibre using shadow-cljs require syntax
            ["maplibre-gl" :as maplibre]))

;; Eiffel Tower coordinates for Paris focus [longitude, latitude]
(def eiffel-tower-coords [2.2945 48.8584])

;; Atom to hold map instance for state management
(def map-instance (reagent/atom nil))

;; Use Reagent's React 18 root instance management
(def react-root (reagent/atom nil))

;; Update the style URLs to use working demo styles
(def style-urls
  {:basic "raster-style"  ;; Custom raster style identifier
   :dark "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
   :light "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"})

;; Atom to track current map style
(def current-style (reagent/atom (:basic style-urls)))

;; Complete raster style configuration for OSM tiles
(def raster-style-config
  (clj->js
   {:version 8
    :name "OSM Bright"
    :center (clj->js eiffel-tower-coords)  ;; Convert coordinates to JS array
    :zoom 15
    :pitch 45
    :bearing 0
    :sources
    {:osm
     {:type "raster"
      :tiles ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"]
      :tileSize 256
      :attribution "© OpenStreetMap contributors"}}
    :layers
    [{:id "osm-tiles"
      :type "raster"
      :source "osm"
      :minzoom 0
      :maxzoom 19}]}))

(defn map-container []
  "Reagent component that renders the map container with proper styling"
  [:div {:id "map-container"
         :style {:width "100%"
                 :height "100vh"
                 :position "absolute"
                 :top 0
                 :left 0}}])

(defn init-map []
  "Initialize Maplibre map with proper style configuration"
  (let [map-element (.getElementById js/document "map-container")]
    (when map-element
      (js/console.log "Initializing map with style:" @current-style)
      (try
        (let [map-config (if (= @current-style "raster-style")
                           (do
                             (js/console.log "Using raster config with OSM tiles")
                             ;; Create complete raster style configuration
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
                           (do
                             (js/console.log "Using vector config with URL:" @current-style)
                             (clj->js {:container "map-container"
                                       :style @current-style
                                       :center (clj->js eiffel-tower-coords)
                                       :zoom 15
                                       :pitch 45
                                       :bearing 0
                                       :attributionControl true
                                       :maxZoom 19
                                       :minZoom 0})))

              ;; Create map instance using imported maplibre module
              map-obj (maplibre/Map. map-config)]

          ;; Store map instance in atom for state management
          (reset! map-instance map-obj)

          ;; Add navigation controls for better UX
          (.addControl map-obj (maplibre/NavigationControl.))

          ;; Add scale control
          (.addControl map-obj (maplibre/ScaleControl.))

          ;; Handle map load completion
          (.on map-obj "load"
               (fn []
                 (js/console.log "Map successfully loaded")
                 (js/console.log "Current style:" @current-style)))
          ;; Handle map errors
          (.on map-obj "error"
               (fn [e]
                 (js/console.error "Map loading error:" e)
                 (js/console.error "Error details:" (.-error e)))))
        (catch js/Error e
          (js/console.error "Failed to initialize map:" e)
          (js/console.error "Stack trace:" (.-stack e)))))))

;; Add error handling to style switching function
(defn change-map-style [style-url]
  (reset! current-style style-url)
  (when-let [^js map-inst @map-instance]
    (try
      ;; Check if it's raster style
      (if (= style-url "raster-style")
        ;; For raster styles, need to recreate map instance
        (do
          (when @map-instance
            (.remove @map-instance))  ;; Remove existing map
          (reset! map-instance nil)
          (js/setTimeout init-map 100))  ;; Delay reinitializing map
        ;; For vector styles, use standard setStyle method
        (.setStyle map-inst style-url))

      (js/console.log "Style changed to:" style-url)
      (catch js/Error e
        (js/console.error "Failed to change style:" e)))))

;; Add style control UI component
(defn style-controls []
  [:div {:style {:position "absolute"
                 :top "20px"
                 :right "20px"
                 :z-index 1000
                 :background "rgba(255,255,255,0.9)"
                 :padding "10px"
                 :border-radius "5px"
                 :font-family "Arial, sans-serif"}}
   [:h3 {:style {:margin "0 0 10px 0"}} "Map Style"]
   [:button {:on-click #(change-map-style (:basic style-urls))
             :style {:margin "5px" :padding "8px 12px" :border "none"
                     :border-radius "3px" :background "#007bff" :color "white"
                     :cursor "pointer"}} "Basic Style"]
   [:button {:on-click #(change-map-style (:dark style-urls))
             :style {:margin "5px" :padding "8px 12px" :border "none"
                     :border-radius "3px" :background "#343a40" :color "white"
                     :cursor "pointer"}} "Dark Style"]
   [:button {:on-click #(change-map-style (:light style-urls))
             :style {:margin "5px" :padding "8px 12px" :border "none"
                     :border-radius "3px" :background "#f8f9fa" :color "black"
                     :cursor "pointer"}} "Light Style"]])

;; Add debug info component to help diagnose issues
(defn debug-info []
  [:div {:style {:position "absolute"
                 :bottom "20px"
                 :left "20px"
                 :z-index 1000
                 :background "rgba(255,255,255,0.9)"
                 :padding "10px"
                 :border-radius "5px"
                 :font-family "Arial, sans-serif"
                 :font-size "12px"}}
   [:div "Map Instance: " (if @map-instance "Loaded" "Not Loaded")]
   [:div "Container: " (if (.getElementById js/document "map-container") "Exists" "Missing")]])

(defn home-page []
  "Main home page component with integrated 3D map"
  [:div
   [:div {:style {:position "absolute"
                  :top "20px"
                  :left "20px"
                  :zIndex 1000
                  :background "rgba(255,255,255,0.9)"
                  :padding "10px"
                  :borderRadius "5px"
                  :fontFamily "Arial, sans-serif"}}
    [:h1 {:style {:margin 0 :fontSize "1.5em" :color "#333"}} "Pearl Map - Paris 3D"]
    [:p {:style {:margin "5px 0 0 0" :fontSize "0.9em" :color "#666"}}
     "Centered at Eiffel Tower (2.2945°E, 48.8584°N)"]
    [:p {:style {:margin "2px 0 0 0" :fontSize "0.8em" :color "#999"}}
     "Using MapLibre demo vector service"]]
   [style-controls]
   [map-container]
   [debug-info]])  ; Add debug info panel

(defn mount-root []
  "Mount the root component using Reagent's React 18 API"
  (let [app-element (.getElementById js/document "app")
        root (rdomc/create-root app-element)]
    (reset! react-root root)
    (rdomc/render root [home-page])
    ;; Initialize map after a short delay to ensure DOM is fully rendered
    (js/setTimeout init-map 100)))

(defn ^:dev/after-load reload []
  "Hot-reload function using Reagent's React 18 API"
  ;; 1. First clean up map instance and DOM elements
  (when @map-instance
    (.remove @map-instance)
    (reset! map-instance nil))

  ;; 2. Clean up map container DOM element
  (let [map-container (.getElementById js/document "map-container")]
    (when map-container
      (let [parent (.-parentNode map-container)
            new-container (.createElement js/document "div")]
        (.setAttribute new-container "id" "map-container")
        (.setAttribute new-container "style" "width:100%;height:100vh;position:absolute;top:0;left:0;")
        (.removeChild parent map-container)
        (.appendChild parent new-container))))

  ;; 3. Reset current style state
  (reset! current-style (:basic style-urls))

  ;; 4. Force re-render all components (update closure references)
  (when @react-root
    (rdomc/render @react-root [home-page]))

  ;; 5. Delay map reinitialization to ensure DOM is fully updated
  (js/setTimeout init-map 100))

(defn init []
  "Application initialization entry point"
  (js/console.log "Initializing Pearl Map application with React 18...")
  (mount-root))
