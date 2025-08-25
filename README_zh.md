# Pearl-Map: 3D 网络地图平台 🌍

[English](README.md) | [中文](README_zh.md)

一个基于 Clojure 和 ClojureScript 构建的高性能、可定制的 3D 网络地图应用程序。使用 MapLibre GL 渲染矢量瓦片和 3D 地形，由 OpenStreetMap 数据驱动，并配备实时样式编辑器以实现动态视觉定制。

## 📖 概述

Pearl-Map 提供沉浸式的 3D 地理空间可视化体验，使用户能够通过直观的界面探索、分析和呈现基于位置的数据。该平台结合了强大的渲染能力和高级定制工具。

## 🏗️ 架构

### 1. 业务架构

**核心价值主张**
提供一个高性能、可定制的 3D 地理空间可视化平台，支持直观地探索、分析和呈现基于位置的数据。

**关键能力**
- **🗺️ 3D 地图核心体验**: 流畅的导航（平移、缩放、倾斜、旋转）、3D 地形渲染、建筑物挤压和自定义 3D 模型集成
- **🎨 动态样式编辑器**: 通过 UI 控件和代码编辑器进行实时视觉定制，支持实时预览和主题共享
- **📊 数据集成与可视化**: 无缝集成 OpenStreetMap，支持 GeoJSON 和基于 API 的地理数据
- **🔍 分析与查询**: 空间要素查询、测量工具，以及未来对高级空间分析的支持

**用户角色**
- **👀 最终查看者**: 探索预配置的地图和可视化
- **✏️ 地图编辑者/分析师**: 使用样式编辑和数据集成工具创建和定制地图视图
- **⚙️ 管理员**: 管理用户、系统配置和后端服务

### 2. 应用架构

**架构风格**: 解耦的前端-后端架构

**前端（单页应用）**
- **技术栈**: ClojureScript, Reagent, re-frame
- **职责**:
  - 使用 React/Reagent 组件渲染 UI
  - 通过统一的 app-db 进行状态管理
  - 通过 MapLibre GL JS 进行地图渲染
  - 使用 Monaco Editor 集成进行样式编辑
  - 通过 HTTP 调用进行 API 通信

**后端（API 服务器）**
- **技术栈**: Clojure, Ring, Reitit, Integrant
- **职责**:
  - RESTful API 网关
  - 业务逻辑和空间查询处理
  - 通过 PostgreSQL/PostGIS 和 next.jdbc 进行数据访问
  - OSM 集成和外部服务代理

**数据流**
```mermaid
flowchart TD
    User[用户浏览器]
    Frontend[前端应用<br/>ClojureScript]
    Backend[后端 API<br/>Clojure]
    Database[数据库<br/>PostGIS]
    Services[外部服务<br/>OSM/第三方]

    User <-->|渲染与交互| Frontend
    Frontend <-->|API 调用| Backend
    Backend <-->|查询/持久化| Database
    Backend <-->|获取数据| Services
    Frontend -.->|直接获取瓦片| Services
```

### 3. 技术栈

| 组件 | 技术 | 理由 |
|-----------|------------|-----------|
| **前端框架** | ClojureScript, Reagent, re-frame | 不可变数据流处理复杂 UI 状态，函数式编程提高可维护性 |
| **地图渲染** | MapLibre GL JS | 开源 WebGL 支持，具有 3D 功能和自定义样式 |
| **样式编辑器** | Monaco Editor | 专业的代码编辑体验，用于样式 JSON |
| **HTTP 客户端** | cljs-ajax/fetch | 强大的 API 通信能力 |
| **前端构建工具** | shadow-cljs | 优越的开发体验，支持热重载和 NPM 集成 |
| **后端构建工具** | deps.edn (Clojure CLI) | 官方工具链，轻量灵活，与 shadow-cljs 集成良好 |
| **后端框架** | Clojure, Ring, Reitit, Integrant | 高性能 JVM 运行时，具有强大的 Web 栈 |
| **数据存储** | PostgreSQL + PostGIS | 空间数据处理的行业标准 |
| **数据格式** | JSON, EDN, MVT | 通用兼容性，支持原生 Clojure |
| **认证** | Buddy | 成熟的安全库，支持 JWT |
| **部署** | Docker, Nginx, JDK | 容器化环境确保一致性 |
| **版本控制** | Git | 标准版本控制系统 |

### 开发环境设置

**前置要求**
- Java Development Kit (JDK) 8+ (推荐 JDK 11 或 17 LTS)
- Node.js 14+
- npm 或 yarn

**安装步骤**
1. 确保已安装符合要求的 Java 版本：
   ```bash
   java -version
   ```
2. 安装 Clojure CLI 工具
3. 安装项目依赖：
   ```bash
   # 安装 JavaScript 依赖
   npm install
   ```
4. 启动开发环境：
   ```bash
   # 启动前端构建和热重载
   npm run dev

   # 在另一个终端启动后端 REPL
   clj -M:dev

   # 启动静态文件服务器（用于开发）
   npm run serve
   ```

**构建生产版本**
```bash
# 构建前端资源
npm run build

# 构建后端 Uberjar（需要额外配置）
```

### 4. 开发路线图

**分阶段策略**: 专注于快速验证、迭代增强和战略扩展

#### 第一阶段: Web 应用基础（核心 MVP）
- **🌐 基于浏览器的 SPA**: 功能齐全的单页应用
- **🏔️ 3D 查看与基本编辑器**: 核心 3D 可视化及基本编辑功能
- **📱 PWA 基础**: 渐进式 Web 应用基础设施设置

#### 第二阶段: 产品增强（增强型 PWA）
- **📴 离线支持与安装**: 启用离线使用和应用安装
- **⚡ 性能优化**: 提升加载速度和渲染性能
- **🔬 高级分析工具**: 添加复杂的空间分析功能

#### 第三阶段: 跨平台扩展（平台扩展）
- **🔌 API 优先平台**: 开发全面的 API 以支持第三方集成
- **📱 移动混合应用**: 使用混合方法扩展到 iOS 和 Android
- **💻 桌面应用**: 原生桌面应用版本

**视觉流程**: 第一阶段 → 第二阶段 → 第三阶段

每个阶段都建立在之前工作的基础上，确保持续增强和扩展能力。

## 🎯 结论

该开发策略遵循低风险、高迭代速度的方法。每个阶段都复用之前的代码，充分利用 Clojure/Script 生态系统的全部潜力。第三阶段的混合移动方法提供了最高效的跨平台覆盖路径。
