(ns pearl-map.build
  (:require [clojure.java.io :as io])
  (:gen-class))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to target/public/css"
  []
  (println "Copying maplibre-gl.css from node_modules...")

  (let [source (io/file "node_modules/maplibre-gl/dist/maplibre-gl.css")
        target-dir (io/file "target/public/css")
        target (io/file target-dir "maplibre-gl.css")]

    ;; Create target directory if it doesn't exist
    (.mkdirs target-dir)

    (if (.exists source)
      (do
        (io/copy source target)
        (println "✓ maplibre-gl.css copied successfully"))
      (println "⚠️  maplibre-gl.css not found in node_modules, please run 'npm install'"))))

(defn -main [& args]
  (copy-maplibre-css))

(defn ^:export build-hook []
  (copy-maplibre-css))
