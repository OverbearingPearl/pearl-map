(ns pearl-map.build
  (:require [clojure.java.io :as io])
  (:gen-class))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to target/public/css"
  []
  (println "Copying maplibre-gl.css from node_modules...")
  (println "Current working directory:" (System/getProperty "user.dir"))

  (let [source (io/file "node_modules/maplibre-gl/dist/maplibre-gl.css")
        target-dir (io/file "target/public/css")
        target (io/file target-dir "maplibre-gl.css")]

    ;; Create target directory if it doesn't exist
    (println "Creating target directory:" (.getAbsolutePath target-dir))
    (.mkdirs target-dir)

    (if (.exists source)
      (do
        (println "Source file exists:" (.getAbsolutePath source))
        (io/copy source target)
        (println "✓ maplibre-gl.css copied successfully to:" (.getAbsolutePath target)))
      (do
        (println "⚠️  maplibre-gl.css not found in node_modules, please run 'npm install'")
        (println "Expected path:" (.getAbsolutePath source))))))

(defn copy-gltf-files
  "Build hook to copy GLTF model files from resources to target directory"
  []
  (println "Copying GLTF model files from resources...")

  (let [source-dir (io/file "resources/public/models/eiffel_tower")
        target-dir (io/file "target/public/models/eiffel_tower")]

    ;; Create target directory if it doesn't exist
    (println "Creating target directory:" (.getAbsolutePath target-dir))
    (.mkdirs target-dir)

    ;; Copy all files in the eiffel_tower directory
    (if (.exists source-dir)
      (do
        (println "Source directory exists:" (.getAbsolutePath source-dir))
        (doseq [file (.listFiles source-dir)]
          (let [target-file (io/file target-dir (.getName file))]
            (io/copy file target-file)
            (println "✓ Copied" (.getName file) "to:" (.getAbsolutePath target-file)))))
      (do
        (println "⚠️  GLTF source directory not found:" (.getAbsolutePath source-dir))
        (println "Expected path:" (.getAbsolutePath source-dir))))))

(defn -main [& args]
  (copy-maplibre-css)
  (copy-gltf-files))

(defn ^:export build-hook []
  (copy-maplibre-css)
  (copy-gltf-files))
