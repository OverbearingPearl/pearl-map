(ns pearl-map.app.db)

(def default-db
  {:map-instance nil
   :current-style "raster-style"
   :custom-layers {}
   :style-editor/editing-style {:fill-color "#f0f0f0"
                                :fill-opacity 1.0
                                :fill-outline-color "#cccccc"}
   :models-3d/model-loaded false
   :models-3d/loaded-model nil
   :models-3d/model-load-error nil})
