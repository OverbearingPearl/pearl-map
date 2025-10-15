(ns pearl-map.utils.geometry
  (:require ["three" :as three]
            ["maplibre-gl" :as maplibregl]))

;; Coordinate transformations using MapLibre
(defn lng-lat-to-world [lng lat]
  (let [coord (.fromLngLat maplibregl/MercatorCoordinate (maplibregl/LngLat. lng lat))]
    [(.-x coord) (.-y coord)]))

(defn world-to-lng-lat [x y]
  (let [coord (.toLngLat (maplibregl/MercatorCoordinate. x y 0))]
    [(.-lng coord) (.-lat coord)]))

;; Screen coordinate conversions
(defn lng-lat-to-screen [map-obj lng lat]
  (when map-obj
    (let [^js map-inst map-obj
          ^js point (.project map-inst (clj->js [lng lat]))]
      [(.-x point) (.-y point)])))

(defn screen-to-lng-lat [map-obj x y]
  (when map-obj
    (let [^js map-inst map-obj
          ^js point (.unproject map-inst (clj->js [x y]))]
      [(.-lng point) (.-lat point)])))

;; Distance calculations using MapLibre
(defn haversine-distance [[lng1 lat1] [lng2 lat2]]
  (.distanceTo (maplibregl/LngLat. lng1 lat1)
               (maplibregl/LngLat. lng2 lat2)))

;; Bounding box operations
(defn calculate-bounds [coordinates]
  (when (seq coordinates)
    (let [lngs (map first coordinates)
          lats (map second coordinates)]
      [(apply min lngs) (apply min lats) (apply max lngs) (apply max lats)])))

;; 3D geometry utilities
(defn create-buffer-geometry [vertices indices]
  {:vertices vertices
   :indices indices})

(defn calculate-model-position [lng lat elevation]
  (let [[x y] (lng-lat-to-world lng lat)]
    [x y (or elevation 0)]))

;; Vector math utilities using Three.js
(defn normalize-vector [[x y z]]
  (let [v (three/Vector3. x y z)]
    (.normalize v)
    [(.-x v) (.-y v) (.-z v)]))

(defn cross-product [[x1 y1 z1] [x2 y2 z2]]
  (let [v1 (three/Vector3. x1 y1 z1)
        v2 (three/Vector3. x2 y2 z2)
        result (three/Vector3.)]
    (.crossVectors result v1 v2)
    [(.-x result) (.-y result) (.-z result)]))

(defn dot-product [[x1 y1 z1] [x2 y2 z2]]
  (.dot (three/Vector3. x1 y1 z1)
        (three/Vector3. x2 y2 z2)))

;; Geometric interpolation
(defn lerp [a b t]
  (+ a (* (- b a) t)))

(defn interpolate-coord [[x1 y1] [x2 y2] t]
  [(lerp x1 x2 t) (lerp y1 y2 t)])

;; Real-world scaling utilities
(defn meters-to-map-units [meters zoom lat]
  "Convert meters to MapLibre mercator units at given zoom level and latitude"
  (let [earth-circumference 40075016.686  ;; Earth circumference in meters at equator
        ;; Calculate circumference at given latitude
        circumference-at-lat (* earth-circumference (js/Math.cos (* lat (/ js/Math.PI 180))))
        tile-size 256
        ;; Calculate meters per pixel at given zoom level and latitude
        meters-per-pixel (/ circumference-at-lat (* tile-size (js/Math.pow 2 zoom)))
        ;; Convert meters to pixels, then to mercator units (0-1 range)
        pixels (/ meters meters-per-pixel)
        mercator-units (/ pixels tile-size)]
    mercator-units))

(defn calculate-real-world-scale [model-height-meters target-height-meters current-model-height]
  "Calculate scale factor to make model match real-world dimensions"
  (let [scale-factor (/ target-height-meters model-height-meters)]
    scale-factor))
