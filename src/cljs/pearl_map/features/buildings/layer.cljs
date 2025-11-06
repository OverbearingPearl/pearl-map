(ns pearl-map.features.buildings.layer
  (:require [re-frame.db :as db]
            [pearl-map.services.map-engine :as map-engine]))

(def ^:private shadow-buckets
  [{:id "short" :min 0 :max 20 :avg 10 :offset 0.0}
   {:id "medium" :min 20 :max 50 :avg 35 :offset 0.1}
   {:id "tall" :min 50 :max 150 :avg 100 :offset 0.2}
   {:id "supertall" :min 150 :max 1000 :avg 300 :offset 0.3}])

(defn update-extrusion-shadow-by-light [light]
  (when-let [^js map-obj (map-engine/get-map-instance)]
    (when light
      (let [[_ a p] (:position light)
            a-rad (* a (/ js/Math.PI 180.0))
            ;; Cap polar angle to avoid infinitely long shadows and visual artifacts (detachment).
            ;; This is a compromise on physical accuracy for better visual quality.
            p-capped (min p 10.0)
            p-rad (* p-capped (/ js/Math.PI 180.0))
            tan-p (js/Math.tan p-rad)
            sin-a (js/Math.sin a-rad)
            cos-a (js/Math.cos a-rad)]
        (doseq [bucket shadow-buckets]
          (let [layer-id (str "extruded-building-shadow-" (:id bucket))]
            (when (.getLayer map-obj layer-id)
              (let [magnitude (* (:avg bucket) tan-p)
                    dx (* magnitude (- sin-a))
                    dy (* magnitude cos-a)]
                (map-engine/set-paint-property layer-id
                                               "fill-extrusion-translate"
                                               (clj->js [dx dy]))))))))))

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

(defn- create-extruded-layer-spec
  ([layer-id initial-color]
   (create-extruded-layer-spec layer-id initial-color nil))
  ([layer-id initial-color bucket]
   (let [is-top-layer? (= layer-id "extruded-building-top")
         is-shadow-layer? (some? bucket)
         height-expr ["coalesce" ["get" "height"] ["get" "render_height"] 10]
         base-expr (if is-top-layer?
                     height-expr
                     ["coalesce" ["get" "min_height"] ["get" "render_min_height"] 0])
         paint-spec (cond
                      is-shadow-layer?
                      {:fill-extrusion-color "black"
                       :fill-extrusion-height ["+" base-expr (+ 1.0 (:offset bucket))]
                       :fill-extrusion-base base-expr
                       :fill-extrusion-opacity 0.3
                       :fill-extrusion-translate [0 0]
                       :fill-extrusion-translate-anchor "map"
                       :fill-extrusion-vertical-gradient false}

                      :else
                      {:fill-extrusion-color initial-color
                       :fill-extrusion-height height-expr
                       :fill-extrusion-base base-expr
                       :fill-extrusion-opacity 1.0
                       :fill-extrusion-vertical-gradient (not is-top-layer?)
                       :fill-extrusion-translate [0, 0]
                       :fill-extrusion-translate-anchor "map"})
         filter (let [exclude-eiffel-tower ["!", ["in", ["id"], ["literal", eiffel-tower-osm-ids]]]]
                  (if is-shadow-layer?
                    ["all"
                     exclude-eiffel-tower
                     [">=", height-expr, (:min bucket)]
                     ["<", height-expr, (:max bucket)]]
                    exclude-eiffel-tower))]
     (clj->js
      {:id layer-id
       :type "fill-extrusion"
       :source "carto"
       :source-layer "building"
       :filter filter
       :paint paint-spec}))))

(defn add-extruded-building-layers []
  (when-let [^js map-obj (map-engine/get-map-instance)]
    (when (.getSource map-obj "carto")
      (let [{:keys [current-style-key lighting]} @db/app-db
            initial-color (if (= current-style-key :dark-style)
                            "#2d3748"
                            "#f0f0f0")]
        (doseq [bucket shadow-buckets]
          (let [layer-id (str "extruded-building-shadow-" (:id bucket))]
            (when-not (.getLayer map-obj layer-id)
              (let [spec (create-extruded-layer-spec layer-id initial-color bucket)]
                (.addLayer map-obj spec)))))
        (doseq [layer-id ["extruded-building" "extruded-building-top"]]
          (when-not (.getLayer map-obj layer-id)
            (let [spec (create-extruded-layer-spec layer-id initial-color)]
              (.addLayer map-obj spec))))
        (update-extrusion-shadow-by-light lighting)))))
