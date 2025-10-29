(ns pearl-map.build
  (:require [clojure.java.io :as io])
  (:gen-class))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to target/public/css"
  []
  (println "Checking maplibre-gl.css...")
  (println "Current working directory:" (System/getProperty "user.dir"))

  (let [source (io/file "node_modules/maplibre-gl/dist/maplibre-gl.css")
        target-dir (io/file "target/public/css")
        target (io/file target-dir "maplibre-gl.css")]

    ;; Create target directory if it doesn't exist
    (println "Ensuring target directory exists:" (.getAbsolutePath target-dir))
    (.mkdirs target-dir)

    ;; Only copy if source exists
    (if (.exists source)
      (do
        (println "Source file exists:" (.getAbsolutePath source))
        (io/copy source target)
        (println "✓ maplibre-gl.css copied successfully to:" (.getAbsolutePath target)))
      (do
        (println "⚠️  maplibre-gl.css not found in node_modules")
        (println "Expected path:" (.getAbsolutePath source))
        (println "Using existing file in target if available")))))

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

(defn copy-index-html
  "Copies index.html from resources/public to target/public."
  []
  (println "Copying index.html...")
  (let [source (io/file "resources/public/index.html")
        target-dir (io/file "target/public")
        target (io/file target-dir "index.html")]
    (.mkdirs target-dir) ; Ensure target/public exists
    (if (.exists source)
      (do
        (io/copy source target)
        (println "✓ index.html copied successfully to:" (.getAbsolutePath target)))
      (println "⚠️  index.html not found at:" (.getAbsolutePath source)))))

(defn copy-main-css
  "Copies style.css from resources/public/css to target/public/css."
  []
  (println "Copying style.css...")
  (let [source (io/file "resources/public/css/style.css")
        target-dir (io/file "target/public/css")
        target (io/file target-dir "style.css")]
    (.mkdirs target-dir) ; Ensure target/public/css exists
    (if (.exists source)
      (do
        (io/copy source target)
        (println "✓ style.css copied successfully to:" (.getAbsolutePath target)))
      (println "⚠️  style.css not found at:" (.getAbsolutePath source)))))

(defn ^:export build-hook []
  (copy-index-html)
  (copy-main-css) ; Also copy main style.css
  (copy-maplibre-css)
  (copy-gltf-files))

(defn -main [& args]
  (println "Running Pearl-Map build tasks...")
  (build-hook)
  (println "✓ Build tasks completed successfully"))
