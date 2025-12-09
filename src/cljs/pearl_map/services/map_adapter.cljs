(ns pearl-map.services.map-adapter
  (:require ["three" :as three]
            ["maplibre-gl" :as maplibre]))

(defn get-model-matrix
  "Calculates the model's local transformation matrix.
  This matrix translates, rotates, and scales the model from its origin
  to its correct position and orientation in the map's world space."
  [{:keys [lng-lat altitude rotation-rad scale]}]
  (let [model-mercator (.fromLngLat maplibre/MercatorCoordinate (clj->js lng-lat) altitude)
        mercator-scale (.meterInMercatorCoordinateUnits model-mercator)
        final-scale (* mercator-scale scale)
        {:keys [x y z]} rotation-rad

        ;; 1. Uniform Scale: Apply model's scale in its local coordinate system.
        uniform-scale-matrix (.makeScale (three/Matrix4.) final-scale final-scale final-scale)

        ;; 2. Rotation: Apply rotations to orient the model correctly.
        rotation-matrix (.makeRotationFromEuler (three/Matrix4.)
                                                (.set (three/Euler.) x y z "XYZ"))

        ;; 3. Coordinate System Flip: Invert Y-axis for MapLibre's coordinate system.
        flip-matrix (.makeScale (three/Matrix4.) 1 -1 1)

        ;; 4. Combine transformations. Order of operations on a vertex is:
        ;; Uniform Scale -> Rotation -> Flip.
        ;; Matrix multiplication order is reversed: Flip * Rotation * UniformScale.
        model-matrix (doto (three/Matrix4.)
                       (.multiply flip-matrix)
                       (.multiply rotation-matrix)
                       (.multiply uniform-scale-matrix))]

    ;; 5. Translation: Move to the final position on the map.
    (.setPosition model-matrix
                  (.-x model-mercator)
                  (.-y model-mercator)
                  (.-z model-mercator))
    model-matrix))

(defn get-camera-matrix
  "Calculates the final projection matrix for the Three.js camera.
  It combines the map's projection matrix with the model's local matrix."
  [{:keys [map-projection-matrix model-matrix]}]
  (-> (.fromArray (three/Matrix4.) map-projection-matrix)
      (.multiply model-matrix)))

(defn convert-light-props
  "Converts MapLibre light properties to a format suitable for Three.js."
  [{:keys [position color intensity]}]
  (when position
    (let [[r a p] position
          a-rad (* a (/ js/Math.PI 180))
          p-rad (* p (/ js/Math.PI 180))
          x (* r (js/Math.sin p-rad) (js/Math.sin a-rad))
          y (* r (js/Math.cos p-rad))
          z (* r (- (js/Math.sin p-rad)) (js/Math.cos a-rad))]
      {:position (three/Vector3. x y z)
       :color (three/Color. color)
       :intensity intensity})))
