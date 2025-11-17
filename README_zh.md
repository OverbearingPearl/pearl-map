# Pearl-Map: 3D 网络地图平台 🌍

[English](README.md) | [中文](README_zh.md)

✨ [在线演示](https://overbearingpearl.github.io/pearl-map/) ✨

一个基于 ClojureScript 构建的高性能、可定制的 3D 网络地图应用程序。使用 MapLibre GL 和 Three.js 渲染矢量瓦片和 3D 模型，由 OpenStreetMap 数据驱动，并配备实时样式编辑器以实现动态视觉定制。

## ✨ 功能特性

- **3D 地图渲染**: 集成 MapLibre GL 进行 2D/3D 地图渲染，并使用 Three.js 渲染自定义 3D 模型（例如埃菲尔铁塔）。
- **多种地图样式**: 支持在栅格（OpenStreetMap）和矢量（CartoDB Positron & Dark Matter）样式之间切换。
- **实时样式编辑器**: 功能强大的动态样式编辑器，适用于矢量地图。可实时定制多种图层类别（包括土地、水系、交通、建筑和标签）的各种属性，如颜色、不透明度、宽度、文本字体等。
- **3D 模型控制**: 调整 3D 模型的缩放和旋转。
- **光照与阴影控制**: 操作环境光和方向光，以控制场景光照和模型阴影。
- **组件化 UI**: 基于 Reagent 和 re-frame 构建，拥有简洁、响应式的用户界面。

## 🚀 快速开始

1.  **克隆仓库：**
    ```bash
    git clone https://github.com/overbearingpearl/pearl-map.git
    cd pearl-map
    ```

2.  **安装依赖：**
    ```bash
    npm install
    ```

3.  **启动开发服务器：**
    ```bash
    npm run dev
    ```
    应用将在 `http://localhost:8080` 上可用。

## 🛠️ 技术栈

| 组件         | 技术                               |
| ------------ | ---------------------------------- |
| **语言**     | ClojureScript                      |
| **UI 框架**  | Reagent (React) & re-frame         |
| **地图渲染** | MapLibre GL JS                     |
| **3D 渲染**  | Three.js                           |
| **构建工具** | shadow-cljs                        |
| **依赖管理** | npm                                |

## 📁 项目结构

```
pearl-map/
├── src/cljs/pearl_map/          # ClojureScript 源代码
│   ├── app/                     # 核心 re-frame 应用 (db, events, subs, views)
│   ├── components/              # 可复用 UI 组件
│   ├── features/                # 功能模块 (建筑, 光照, 3D模型, 样式编辑器)
│   ├── services/                # 外部服务集成 (地图引擎, 模型加载器)
│   ├── utils/                   # 辅助函数 (颜色, 几何计算)
│   └── core.cljs                # 应用入口点
├── resources/public/            # 静态资源 (HTML, CSS, 3D 模型)
├── deps.edn                     # Clojure 依赖
├── package.json                 # JavaScript 依赖
└── shadow-cljs.edn              # shadow-cljs 构建配置
```

## 🗺️ 地图样式

应用程序支持三种地图样式：
- **基础样式**: 来自 OpenStreetMap 的简单栅格瓦片样式。
- **深色样式**: 来自 CartoDB Dark Matter 的矢量瓦片样式，支持建筑样式编辑。
- **浅色样式**: 来自 CartoDB Positron 的矢量瓦片样式，同样支持建筑样式编辑。

## 🛣️ 路线图

- 实现用于 3D 地图导航的倾斜和旋转控件。
- 扩展样式编辑器以支持更多图层属性。
- 为 3D 模型添加更多交互式控件。
- 引入后端服务以实现数据持久化和 API 功能。
