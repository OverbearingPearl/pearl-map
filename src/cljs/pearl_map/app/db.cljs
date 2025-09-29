(ns pearl-map.app.db)

(def default-db
  {:map-instance nil
   :current-style "raster-style"
   :model-loaded false
   :loaded-model nil
   :custom-layers {}
   :style-editor/editing-style {:fill-color "#f0f0f0"
                                :fill-opacity 0.7
                                :fill-outline-color "#cccccc"}})
