(ns pearl-map.config)

(def eiffel-tower-coords [2.2944800 48.8582700])
(def eiffel-tower-real-height 310)
(def eiffel-tower-initial-scale 1)
(def eiffel-tower-initial-rotation-z 45)
(def model-layer-id "3d-model-eiffel")

(def default-inspect-zoom 17)

(def style-urls
  {:raster-style "raster-style"
   :dark-style "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
   :light-style "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"})

(def raster-style-object
  {:version 8
   :name "OSM Bright"
   :sources {:osm {:type "raster"
                   :tiles ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"]
                   :tileSize 256
                   :attribution "© OpenStreetMap contributors"}}
   :layers [{:id "osm-tiles"
             :type "raster"
             :source "osm"
             :minzoom 0
             :maxzoom 19}]})

(def model-altitude 0)
(def model-rotate [(/ js/Math.PI 2) 0 0])

(def eiffel-tower-osm-ids
  "OSM IDs for buildings in the Eiffel Tower complex to be excluded from the map."
  [;; Main structure:
   5013364
   ;; Other structures that have little impact (IDs identified via click debugging):
   278644
   ;; 279659
   ;; 540568
   ;; 540590]
   ;; Surrounding structures:
   308687745
   308687744
   308689164
   4114842
   4114839
   308687746
   308145239
   69034127
   335101043
   4114841])
