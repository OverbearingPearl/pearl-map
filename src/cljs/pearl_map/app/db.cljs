(ns pearl-map.app.db)

(def default-db
  {:map-instance nil
   :current-style "raster-style"
   :custom-layers {}
   :style-editor/editing-style {:fill-color "#f0f0f0"
                                :fill-opacity 1.0
                                :fill-outline-color "#cccccc"}
   :map/light-properties {:anchor "viewport"
                          :color "#ffffff"
                          :intensity 0.5
                          :position [1.15 210 30]}
   :models-3d/eiffel-loaded? false
   :models-3d/eiffel-scale 1.0
   :models-3d/eiffel-rotation-z 45.0
   :show-other-components? true})
