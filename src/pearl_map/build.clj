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

    ;; Only copy if source exists and target doesn't exist
    (if (.exists source)
      (if (.exists target)
        (println "✓ maplibre-gl.css already exists in target, skipping copy")
        (do
          (println "Source file exists:" (.getAbsolutePath source))
          (io/copy source target)
          (println "✓ maplibre-gl.css copied successfully to:" (.getAbsolutePath target))))
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

;; Add Three.js file copying function with overwrite behavior
(defn copy-threejs-files
  "Build hook to copy Three.js files from node_modules to target directory"
  []
  (println "Copying Three.js files from node_modules...")

  (let [source-dir (io/file "node_modules/three")
        target-dir (io/file "target/public/js/three")
        source-gltf (io/file "node_modules/three/examples/jsm/loaders")
        target-gltf (io/file "target/public/js/three/loaders")]

    ;; Create target directories if they don't exist
    (.mkdirs target-dir)
    (.mkdirs target-gltf)

    ;; Copy Three.js main library (always overwrite)
    (if (.exists source-dir)
      (do
        (println "Three.js source directory exists:" (.getAbsolutePath source-dir))
        ;; Copy the main three.js file
        (let [source-file (io/file source-dir "build/three.min.js")
              target-file (io/file target-dir "three.min.js")]
          (when (.exists source-file)
            (io/copy source-file target-file)
            (println "✓ Copied/overwritten three.min.js to:" (.getAbsolutePath target-file)))))
      (println "⚠️  Three.js not found in node_modules, please run 'npm install'"))

    ;; Copy GLTFLoader (always overwrite)
    (if (.exists source-gltf)
      (do
        (println "GLTFLoader source directory exists:" (.getAbsolutePath source-gltf))
        ;; Copy the GLTFLoader file
        (let [source-file (io/file source-gltf "GLTFLoader.js")
              target-file (io/file target-gltf "GLTFLoader.js")]
          (when (.exists source-file)
            (io/copy source-file target-file)
            (println "✓ Copied/overwritten GLTFLoader.js to:" (.getAbsolutePath target-file)))))
      (println "⚠️  GLTFLoader not found in node_modules, please run 'npm install'"))))

(defn ^:export build-hook []
  (copy-maplibre-css)
  (copy-gltf-files)
  (copy-threejs-files))

;; Add the missing -main function
(defn -main [& args]
  (println "Running Pearl-Map build tasks...")
  (build-hook)
  (println "✓ Build tasks completed successfully"))
