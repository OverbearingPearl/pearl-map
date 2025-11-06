# Pearl-Map: 3D Web Mapping Platform 🌍

[English](README.md) | [中文](README_zh.md)

✨ [Live Demo](https://overbearingpearl.github.io/pearl-map/) ✨

A high-performance, customizable 3D web mapping application built with ClojureScript. Renders vector tiles and 3D models using MapLibre GL and Three.js, powered by OpenStreetMap data, and features a real-time style editor for dynamic visual customization.

## ✨ Features

- **3D Map Rendering**: Integrates MapLibre GL for 2D/3D map rendering and Three.js for custom 3D model rendering (e.g., the Eiffel Tower).
- **Multiple Map Styles**: Supports switching between Raster (OpenStreetMap) and Vector (CartoDB Positron & Dark Matter) styles.
- **Real-time Style Editor**: Dynamically edit building layer styles (fill color, outline color, opacity) on vector maps.
- **3D Model Controls**: Adjust 3D model scale and rotation.
- **Lighting and Shadow Control**: Manipulate ambient and directional light to control scene lighting and model shadows.
- **Component-based UI**: Built with Reagent and re-frame, featuring a clean, responsive UI.

## 🚀 Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/overbearingpearl/pearl-map.git
    cd pearl-map
    ```

2.  **Install dependencies:**
    ```bash
    npm install
    ```

3.  **Start the development server:**
    ```bash
    npm run dev
    ```
    The application will be available at `http://localhost:8080`.

## 🛠️ Technology Stack

| Component          | Technology                               |
| ------------------ | ---------------------------------------- |
| **Language**       | ClojureScript                            |
| **UI Framework**   | Reagent (React) & re-frame               |
| **Map Rendering**  | MapLibre GL JS                           |
| **3D Rendering**   | Three.js                                 |
| **Build Tool**     | shadow-cljs                              |
| **Dependencies**   | npm                                      |

## 📁 Project Structure

```
pearl-map/
├── src/cljs/pearl_map/          # ClojureScript source code
│   ├── app/                     # Core re-frame app (db, events, subs, views)
│   ├── components/              # Reusable UI components
│   ├── features/                # Feature modules (map, style editor, 3D models)
│   ├── services/                # External service integrations (map engine)
│   └── core.cljs                # Application entry point
├── resources/public/            # Static assets (HTML, CSS, 3D models)
├── deps.edn                     # Clojure dependencies
├── package.json                 # JavaScript dependencies
└── shadow-cljs.edn              # shadow-cljs build configuration
```

## 🗺️ Map Styles

The application supports three map styles:
- **Basic**: A simple raster tile style from OpenStreetMap.
- **Dark**: A vector tile style from CartoDB Dark Matter, which supports building style editing.
- **Light**: A vector tile style from CartoDB Positron, which also supports building style editing.

## 🛣️ Roadmap

- Implement tilt and rotation controls for 3D map navigation.
- Expand the style editor to support more layer properties.
- Add more interactive controls for 3D models.
- Introduce a backend service for data persistence and API.
