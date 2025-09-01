(ns pearl-map.build
  (:require [clojure.java.io :as io])
  (:gen-class))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to resources/public/css"
  []
  (println "Copying maplibre-gl.css from node_modules...")

  (let [source (io/file "node_modules/maplibre-gl/dist/maplibre-gl.css")
        target-dir (io/file "resources/public/css")
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
(ns pearl-map.build
  (:require [clojure.java.io :as io]))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to resources/public/css"
  []
  (println "Copying maplibre-gl.css from node_modules...")

  (let [source (io/file "node_modules/maplibre-gl/dist/maplibre-gl.css")
        target-dir (io/file "resources/public/css")
        target (io/file target-dir "maplibre-gl.css")]

    (.mkdirs target-dir)

    (when (.exists source)
      (io/copy source target)
      (println "✓ maplibre-gl.css copied successfully"))

    (when (not (.exists source))
      (println "⚠️  maplibre-gl.css not found in node_modules"))))

(defn ^:export build-hook []
  (copy-maplibre-css))
(ns pearl-map.build
  (:require [clojure.java.io :as io]))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to resources/public/css"
  []
  (println "Copying maplibre-gl.css from node_modules...")

  (let [source (io/file "node_modules/maplibre-gl/dist/maplibre-gl.css")
        target-dir (io/file "resources/public/css")
        target (io/file target-dir "maplibre-gl.css")]

    ;; Create target directory if it doesn't exist
    (.mkdirs target-dir)

    (when (.exists source)
      (io/copy source target)
      (println "✓ maplibre-gl.css copied successfully"))

    (when (not (.exists source))
      (println "⚠️  maplibre-gl.css not found in node_modules, please run 'npm install'"))))

(defn ^:export build-hook []
  (copy-maplibre-css))
