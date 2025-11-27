(ns pearl-map.build
  (:require [clojure.java.io :as io])
  (:import [java.util Base64])
  (:gen-class))

(defn- log-info [message]
  (println (str "INFO: " message)))

(defn- log-warn [message]
  (binding [*out* *err*]
    (println (str "WARN: " message))))

(defn- log-error [message]
  (binding [*out* *err*]
    (println (str "ERROR: " message))))

(defn- exit-with-error [message]
  (log-error message)
  (System/exit 1))

(defn copy-file!
  "Copies a file from source to target, creating target directory if needed.
  Exits with error if source file does not exist."
  [source-path target-path]
  (let [source (io/file source-path)
        target (io/file target-path)
        target-dir (.getParentFile target)]
    (log-info (str "Copying " source-path " to " target-path))
    (when-not (.exists target-dir)
      (log-info (str "Creating target directory: " (.getAbsolutePath target-dir)))
      (when-not (.mkdirs target-dir)
        (exit-with-error (str "Failed to create target directory: " (.getAbsolutePath target-dir)))))
    (if (.exists source)
      (try
        (io/copy source target)
        (log-info (str "✓ Successfully copied " (.getName source)))
        (catch Exception e
          (exit-with-error (str "Failed to copy " (.getName source) ": " (.getMessage e)))))
      (exit-with-error (str "Source file not found: " (.getAbsolutePath source))))))

(defn copy-directory!
  "Copies all files from source directory to target directory recursively.
  Exits with error if source directory does not exist."
  [source-dir-path target-dir-path]
  (let [source-dir (io/file source-dir-path)
        target-dir (io/file target-dir-path)]
    (log-info (str "Copying directory " source-dir-path " to " target-dir-path))
    (when-not (.exists target-dir)
      (log-info (str "Creating target directory: " (.getAbsolutePath target-dir)))
      (when-not (.mkdirs target-dir)
        (exit-with-error (str "Failed to create target directory: " (.getAbsolutePath target-dir)))))
    (if (.exists source-dir)
      (doseq [file (.listFiles source-dir)]
        (let [child-source (.getAbsolutePath file)
              child-target (str (.getAbsolutePath target-dir) "/" (.getName file))]
          (if (.isDirectory file)
            (copy-directory! child-source child-target)
            (copy-file! child-source child-target))))
      (exit-with-error (str "Source directory not found: " (.getAbsolutePath source-dir))))))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to target/public/css"
  []
  (log-info "Checking maplibre-gl.css...")
  (copy-file! "node_modules/maplibre-gl/dist/maplibre-gl.css" "target/public/css/maplibre-gl.css"))

(defn copy-model-assets
  "Copies all model assets (glb, license, etc) to target directory."
  []
  (log-info "Copying model assets...")
  (let [source-dir "resources/public/models/eiffel_tower"
        target-dir "target/public/models/eiffel_tower"]
    (copy-directory! source-dir target-dir)))

(defn copy-index-html
  "Copies index.html from resources/public to target/public."
  []
  (copy-file! "resources/public/index.html" "target/public/index.html"))

(defn copy-main-css
  "Copies style.css from resources/public/css to target/public/css."
  []
  (copy-file! "resources/public/css/style.css" "target/public/css/style.css"))

(defn copy-favicon
  "Copies favicon.jpg from resources/public to target/public."
  []
  (copy-file! "resources/public/favicon.jpg" "target/public/favicon.jpg"))

(defn copy-draco-assets
  "Copies Draco decoder files from node_modules to target directory."
  []
  (log-info "Copying Draco decoder assets...")
  (let [source-dir "node_modules/three/examples/jsm/libs/draco"
        target-dir "target/public/js/libs/draco"]
    (copy-directory! source-dir target-dir)))

(defn ^:export build-hook []
  (log-info "Starting build hook tasks...")
  (copy-index-html)
  (copy-favicon)
  (copy-main-css)
  (copy-maplibre-css)
  (copy-model-assets)
  (copy-draco-assets)
  (log-info "All build hook tasks completed."))

(defn -main [& args]
  (log-info "Running Pearl-Map build tasks...")
  (build-hook)
  (log-info "✓ Pearl-Map build tasks completed successfully"))
