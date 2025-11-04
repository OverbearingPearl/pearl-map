(ns pearl-map.features.buildings.layer
  (:require [re-frame.db :as db]
            [pearl-map.services.map-engine :as map-engine]))

(def eiffel-tower-osm-ids
  "OSM IDs for buildings in the Eiffel Tower complex to be excluded from the map."
  [;; Main structure:
   5013364
   ;; Other structures that have little impact (IDs identified via click debugging):
   ;; 278644
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

(defn- create-extruded-layer-spec [layer-id initial-color]
  (let [is-top-layer? (= layer-id "extruded-building-top")
        height-expr ["coalesce" ["get" "height"] ["get" "render_height"] 10]
        base-expr (if is-top-layer?
                    height-expr
                    ["coalesce" ["get" "min_height"] ["get" "render_min_height"] 0])]
    (clj->js
     {:id layer-id
      :type "fill-extrusion"
      :source "carto"
      :source-layer "building"
      :filter (into ["!in" "$id"] eiffel-tower-osm-ids)
      :paint {:fill-extrusion-color initial-color
              :fill-extrusion-height height-expr
              :fill-extrusion-base base-expr
              :fill-extrusion-opacity 1.0
              :fill-extrusion-vertical-gradient (not is-top-layer?)
              :fill-extrusion-translate [0, 0]
              :fill-extrusion-translate-anchor "map"}})))

(defn add-extruded-buildings-layer []
  (when-let [^js map-obj (map-engine/get-map-instance)]
    (when (.getSource map-obj "carto")
      (let [current-style (:current-style @db/app-db)
            dark-style-url "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
            initial-color (if (= current-style dark-style-url)
                            "#2d3748"
                            "#f0f0f0")]
        (doseq [layer-id ["extruded-building" "extruded-building-top"]]
          (when-not (.getLayer map-obj layer-id)
            (let [spec (create-extruded-layer-spec layer-id initial-color)]
              (.addLayer map-obj spec))))))))
