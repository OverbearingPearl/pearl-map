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

(defn- embed-gltf-model!
  "Reads a GLB model, base64-encodes it, and saves it as a data URI in a ClojureScript file."
  []
  (log-info "Embedding GLB model into a ClojureScript file...")
  (let [source-path "resources/public/models/eiffel_tower/scene.glb"
        target-path "src/cljs/pearl_map/features/models_3d/model_data.cljs"
        source-file (io/file source-path)]
    (if-not (.exists source-file)
      (exit-with-error (str "Source model file not found: " source-path))
      (try
        (let [byte-arr (with-open [is (io/input-stream source-file)]
                         (let [buffer (byte-array (.length source-file))]
                           (.read is buffer)
                           buffer))
              encoded (.encodeToString (Base64/getEncoder) byte-arr)
              data-uri (str "data:model/gltf-binary;base64," encoded)
              cljs-content (str "(ns pearl-map.features.models-3d.model-data)\n\n"
                                "(def eiffel-tower-model-data\n  \"" data-uri "\")\n")]
          (spit target-path cljs-content)
          (log-info (str "✓ Successfully embedded model into " target-path)))
        (catch Exception e
          (exit-with-error (str "Failed to embed model: " (.getMessage e))))))))

(defn copy-directory!
  "Copies all files from source directory to target directory.
  Exits with error if source directory does not exist."
  [source-dir-path target-dir-path]
  (let [source-dir (io/file source-dir-path)
        target-dir (io/file target-dir-path)]
    (log-info (str "Copying files from " source-dir-path " to " target-dir-path))
    (when-not (.exists target-dir)
      (log-info (str "Creating target directory: " (.getAbsolutePath target-dir)))
      (when-not (.mkdirs target-dir)
        (exit-with-error (str "Failed to create target directory: " (.getAbsolutePath target-dir)))))
    (if (.exists source-dir)
      (doseq [file (.listFiles source-dir)]
        (copy-file! (.getAbsolutePath file) (str (.getAbsolutePath target-dir) "/" (.getName file))))
      (exit-with-error (str "Source directory not found: " (.getAbsolutePath source-dir))))))

(defn copy-maplibre-css
  "Build hook to automatically copy maplibre CSS from node_modules to target/public/css"
  []
  (log-info "Checking maplibre-gl.css...")
  (copy-file! "node_modules/maplibre-gl/dist/maplibre-gl.css" "target/public/css/maplibre-gl.css"))

(defn copy-model-license
  "Build hook to copy model license from resources to target directory."
  []
  (log-info "Copying model license file...")
  (let [source-dir "resources/public/models/eiffel_tower"
        target-dir "target/public/models/eiffel_tower"]
    (copy-file! (str source-dir "/license.txt") (str target-dir "/license.txt"))))

(defn copy-index-html
  "Copies index.html from resources/public to target/public."
  []
  (copy-file! "resources/public/index.html" "target/public/index.html"))

(defn copy-main-css
  "Copies style.css from resources/public/css to target/public/css."
  []
  (copy-file! "resources/public/css/style.css" "target/public/css/style.css"))

(defn ^:export build-hook []
  (log-info "Starting build hook tasks...")
  (embed-gltf-model!)
  (copy-index-html)
  (copy-main-css)
  (copy-maplibre-css)
  (copy-model-license)
  (log-info "All build hook tasks completed."))

(defn- cleanup-generated-files! []
  (log-info "Cleaning up generated files...")
  (let [model-data-file (io/file "src/cljs/pearl_map/features/models_3d/model_data.cljs")]
    (when (.exists model-data-file)
      (io/delete-file model-data-file)
      (log-info (str "✓ Deleted " (.getPath model-data-file))))))

(defn -main [& args]
  (if (= (first args) "--cleanup")
    (cleanup-generated-files!)
    (do
      (log-info "Running Pearl-Map build tasks...")
      (build-hook)
      (log-info "✓ Pearl-Map build tasks completed successfully"))))
