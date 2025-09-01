(ns pearl-map.core
  (:require [reagent.core :as reagent]
            [reagent.dom :as rdom]
            ;; Import maplibre using shadow-cljs require syntax
            ["maplibre-gl" :as maplibre]
            ["react-dom/client" :as react-dom]))  ;; Import React 18 client

;; Eiffel Tower coordinates for Paris focus [longitude, latitude]
(def eiffel-tower-coords [2.2945 48.8584])

;; Atom to hold map instance for state management
(def map-instance (reagent/atom nil))

;; Atom to hold React root instance
(def react-root (reagent/atom nil))

(defn map-container []
  "Reagent component that renders the map container with proper styling"
  [:div {:id "map-container"
         :style {:width "100%"
                 :height "100vh"
                 :position "absolute"
                 :top 0
                 :left 0}}])

(defn init-map []
  "Initialize Maplibre map using tile.openstreetmap.de as tile server"
  (let [map-element (.getElementById js/document "map-container")]
    (when map-element
      ;; Custom style using tile.openstreetmap.de which is accessible in China
      (let [map-style (clj->js
                       {:version 8
                        :sources
                        {:openstreetmap
                         {:type "raster"
                          :tiles ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"]
                          :tileSize 256
                          :attribution "© OpenStreetMap contributors"}}
                        :layers
                        [{:id "osm-tiles"
                          :type "raster"
                          :source "openstreetmap"
                          :minzoom 0
                          :maxzoom 19}]})

            map-options (clj->js
                         {:container "map-container"
                          :style map-style
                          :center eiffel-tower-coords
                          :zoom 15
                          :pitch 45  ; Enable 3D perspective
                          :bearing 0
                          :attributionControl true
                          :maxZoom 19
                          :minZoom 0})

            ;; Create map instance using imported maplibre module
            map-obj (maplibre/Map. map-options)]

        ;; Store map instance in atom for state management
        (reset! map-instance map-obj)

        ;; Add navigation controls for better UX
        (.addControl map-obj (maplibre/NavigationControl.))

        ;; Add scale control
        (.addControl map-obj (maplibre/ScaleControl.))

        ;; Handle map load completion
        (.on map-obj "load"
             (fn []
               (js/console.log "Map successfully loaded with Paris focus")
               (js/console.log "Using tile.openstreetmap.de as tile server")
               (js/console.log "Eiffel Tower coordinates:" eiffel-tower-coords)))))))

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
     "Tile server: tile.openstreetmap.de"]]
   [map-container]])

(defn mount-root []
  "Mount the root component using React 18 createRoot API"
  (let [app-element (.getElementById js/document "app")
        root (react-dom/createRoot app-element)]
    ;; Store root instance for cleanup
    (reset! react-root root)
    (.render root (reagent/as-element [home-page]))
    ;; Initialize map after a short delay to ensure DOM is fully rendered
    (js/setTimeout init-map 100)))

(defn ^:dev/after-load reload []
  "Hot-reload function for development with React 18"
  (when @react-root
    (.render @react-root (reagent/as-element [home-page])))
  (js/console.log "Hot reload completed"))

(defn init []
  "Application initialization entry point"
  (js/console.log "Initializing Pearl Map application with React 18...")
  (mount-root))
