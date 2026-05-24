# EngReader Best

> Android EPUB 英文原版书阅读器 — 集成多 AI 翻译引擎 | v1.0.2

[![Release](https://img.shields.io/github/v/release/Mengchuan-Yang/EngReader-Best?label=latest)](https://github.com/Mengchuan-Yang/EngReader-Best/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2014%2B-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/compose-Material%203-purple)](https://developer.android.com/compose)

---

## 项目简介

EngReader Best 是一款面向英语学习者和原版书读者的 Android EPUB 阅读器。核心特色是**点击即译**——阅读英文原版书时，点按任意单词即可获得 AI 结合上下文给出的中文释义，长按自动提取当前句子并翻译。翻译结果以红色小字直接标注在原文旁边，无需跳出阅读界面。

项目集成了 **DeepSeek、Gemini、OpenAI** 三家 AI 翻译后端，支持按优先级自动 fallback，任一 provider 不可用时自动切换下一个，保证翻译服务的高可用性。

---

## 功能特性

### 阅读体验

| 功能 | 说明 |
|------|------|
| 连续滚动 | 全书所有章节平铺在单个滚动列表中，读完整章自动进入下一章，无需手动切换 |
| 沉浸式全屏 | 阅读时自动隐藏状态栏和导航栏；点击标点/空白区域或章节标题即可唤出控制栏 |
| HTML 样式保留 | 粗体、斜体、下划线、删除线、上下标等 EPUB 内嵌样式完整保留并正确渲染 |
| EPUB 内嵌图片 | `<img>` 标签图片自动提取到本地存储，在正文中按原始位置渲染 |
| 两端对齐 + 连字符 | 可选择左对齐或两端对齐；两端对齐模式下整行撑满，行尾长单词自动用 `-` 断开换行 |
| 阅读样式调节 | 字体大小 (0.8x–2.0x)、行间距 (1.1x–2.2x)、段落间距 (0.5x–3.0x)、页边距 (0.5x–3.0x) 均可独立调节 |
| 大文件懒加载 | 打开书籍时仅解析前 5 章，滚动接近未解析章节时按需加载，避免首开卡顿 |
| 封面提取 | 导入书籍时立即自动提取封面缩略图，无需先打开阅读 |
| 自适应图标 | Material You 主题取色，深蓝底打开书本 + 红色书签，支持 Android 13+ monochrome 主题图标 |

### AI 翻译

| 功能 | 说明 |
|------|------|
| 点按查词 | 点击英文单词 → AI 结合上下文给出 1–2 个最准确的中文释义（全屏模式下同样可用） |
| 长按翻句 | 长按段落 → 自动提取当前句子（以 `. ! ?` 为界）而非整段，译文直接跟随在句子后面 |
| 多 Provider | DeepSeek / Gemini / OpenAI 三路后端，通过 API Settings 配置各自的 Key |
| Fallback 容错 | 按优先级链 `[DeepSeek → Gemini → OpenAI]` 自动切换，单点故障不影响使用 |
| 翻译标注 | 红色小字 `（译文）` 直接插入在原文单词/句子后方，可逐条删除 |
| 重复模式 | 翻译一个词后，可选择整章 / 全书自动扫描并标注所有出现位置 |
| 缓存去重 | 已翻译内容本地缓存，再次点击不会重复调用 API |

### 书架管理

| 功能 | 说明 |
|------|------|
| 侧边栏菜单 | 点击 ☰ 展开，集中收纳视图切换、排序、主题、API 设置、备份/还原 |
| 网格 / 列表视图 | 网格模式显示封面缩略图，列表模式紧凑显示 |
| 排序 | 最近阅读 / 导入时间 / 书名 |
| 阅读进度 | 百分比显示（已读章节/总章节） |
| 备份 / 恢复 | ZIP 格式，包含全部书籍文件、阅读进度、书签、翻译标注 |

### 其他

- **书签**：一键添加/删除，弹窗列表查看
- **全文搜索**：在当前书中搜索关键词，点击结果跳转到对应段落
- **目录跳转**：TOC 弹窗展示全部章节，点击直接跳转
- **安全存储**：API Key 使用 `EncryptedSharedPreferences` 加密

---

## 架构设计

### 整体架构

项目采用 **MVVM (Model-View-ViewModel)** 架构，单 Activity + Compose Navigation。

```
┌─────────────────────────────────────────────────────────┐
│                     View Layer (Compose)                 │
│  MainScreen.kt  ─── 书架界面 + 阅读器界面                │
│  ShelfContent / ReaderContent / 各种 Dialog              │
├─────────────────────────────────────────────────────────┤
│                   ViewModel Layer                        │
│  MainScreenViewModel.kt  ─── 全部状态管理                │
│  MainUiState / ReaderUiState  ─── 不可变状态快照         │
├──────────────┬──────────────┬───────────────────────────┤
│   Storage    │    AI Layer  │      Settings             │
│   EpubText   │  Fallback   │  SecureApiKeyStore        │
│   Extractor  │  Translation│  (EncryptedSharedPrefs)   │
│   EpubImport │  Service    │                           │
│   Service    │  ├─ DeepSeek│                           │
│   AppState   │  ├─ Gemini  │                           │
│   Store      │  └─ OpenAI  │                           │
│   BackupZip  │             │                           │
│   Service    │             │                           │
├──────────────┴─────────────┴───────────────────────────┤
│                    Model Layer                           │
│  Models.kt  ─── BookRecord / ChapterContent /            │
│  AnnotationRecord / ReaderSettings / ParagraphSegment    │
├─────────────────────────────────────────────────────────┤
│                 Data Layer (预置，待接入)                 │
│  Room Database / DAOs / Repositories                     │
│  EngReaderDatabase  ─── 表: books, annotations,          │
│  bookmarks, reading_progress, app_settings               │
└─────────────────────────────────────────────────────────┘
```

### 数据流：点击单词 → 翻译标注

```
用户点击单词
    │
    ▼
MainScreen.kt: ParagraphWithGestures
    │  detectTapGestures.onTap → 获取像素坐标
    │  TextLayoutResult.getOffsetForPosition() → 字符偏移量
    │  mapRenderedOffsetToOriginal() → 原文偏移量
    ▼
MainScreenViewModel.translateWordAt(chapter, paraIdx, text, offset)
    │  extractWordAtOffset() → 提取点击处的英文单词
    │  hasWordAnnotation() → 检查本地缓存
    │  FallbackTranslationService.translateWord()
    │      ├── DeepSeek  ──失败──▶
    │      ├── Gemini    ──失败──▶
    │      └── OpenAI
    ▼
AppStateStore.addAnnotation()
    │  写入 JSON 文件 engreader_state.json
    │  更新 StateFlow<PersistedState>
    ▼
UI 重组: readerState.annotations 变化
    │  renderParagraphWithAnnotations()
    │  在原文单词后插入 （译文） 标记
    ▼
用户看到: "hello（你好） world"
```

### 模块职责

| 模块 | 路径 | 职责 |
|------|------|------|
| **UI** | `ui/main/` | Compose 界面：书架 (`ShelfContent`)、阅读器 (`ReaderContent`)、弹窗 (TOC/搜索/书签/样式/API) |
| **ViewModel** | `ui/main/MainScreenViewModel.kt` | 全部状态管理，通过 `StateFlow<MainUiState>` 驱动 UI 重组 |
| **Models** | `model/Models.kt` | 核心数据类：`BookRecord`, `ChapterContent`, `AnnotationRecord`, `ReaderSettings`, `ParagraphSegment` |
| **EPUB 解析** | `storage/EpubTextExtractor.kt` | epub4j 封装：元数据提取、封面提取、章节解析、HTML→AnnotatedString 样式转换、图片提取、懒加载 |
| **EPUB 导入** | `storage/EpubImportService.kt` | 文件导入：从 URI 复制到应用私有目录 `filesDir/books/` |
| **数据持久化** | `storage/AppStateStore.kt` | JSON 文件存储：`engreader_state.json`，kotlinx.serialization 全量序列化 |
| **备份恢复** | `storage/BackupZipService.kt` | ZIP 打包：state.json + 全部 .epub 文件 |
| **AI 翻译** | `ai/` | 多 Provider 翻译：`FallbackTranslationService` 协调 fallback 链，`NetworkProviderClients` 封装 HTTP 请求 |
| **安全存储** | `settings/SecureApiKeyStore.kt` | API Key 加密：AndroidX `EncryptedSharedPreferences` |
| **主题** | `theme/` | Material 3 色彩方案：日间/夜间/系统三套配色 |
| **Room 数据层** | `data/` + `domain/` | Room 数据库 Schema（已编译，待接入 ViewModel） |

### 关键设计决策

**为什么用 JSON 而非 Room？**
项目初期为快速迭代选择了 JSON 文件方案 (`AppStateStore`)。Room 数据库架构 (`data/` + `domain/`) 已完整定义并通过编译，计划在后续版本中逐步迁移。

**为什么用 `AnnotatedString` 渲染而非 WebView？**
WebView 方案渲染丰富但无法精确获取点击位置的字符偏移量，无法实现"点击单词查词"的核心交互。Compose `BasicText` + `TextLayoutResult.getOffsetForPosition()` 提供了像素→字符偏移的精确映射。

**为什么 epub4j 而非其他 EPUB 库？**
epub4j 是 Java 生态中最成熟的 EPUB 解析库之一，支持 EPUB 2/3，API 简洁。其局限是不支持流式读取（需一次性加载到内存）。

---

## 开发历史

本项目由 AI 代理接力开发，从零到 1.0 共经历四轮迭代。

### 第一轮（2026-05-23 上午）— 项目奠基

- Gradle 构建配置 (AGP 9.0 + Kotlin 2.3 + Compose BOM 2026.03)
- Compose UI 骨架：单 Activity + 导航切换
- epub4j 集成：EPUB 解析、章节提取、元数据读取
- DeepSeek API 翻译客户端
- 基础书架（卡片列表）和阅读页（垂直滚动）
- **遗留问题**：书名乱码、色彩不全、edge-to-edge 未完成、分页算法有误、TOC 乱码、暗色模式不可读

### 第二轮（2026-05-23 下午）— 修复迭代

- 书名/TOC 乱码修复（`looksLikeHashOrId` 过滤算法）
- Material 3 色彩方案完善（8 色完整 darkColorScheme/lightColorScheme）
- Edge-to-edge 沉浸式修复
- 分页算法重构（动态 `maxCharsPerPage` 基于屏幕和字体）
- 阅读页交互重构（chrome 可见性、TopAppBar 配色）
- 多 provider fallback 机制、API Key 加密存储

### 第三轮（2026-05-23 晚间）— UX 打磨

- BackHandler 侧滑返回逻辑
- Material Design 3 完整重构：BottomBar 控制栏、FAB 导入、Style 弹窗
- 段落间距/页边距调节、阅读样式弹窗化
- 翻译 API 主线阻塞修复（`withContext(Dispatchers.IO)`）
- 封面提取、阅读进度百分比、模型名修正
- 产物：v0.1.0-pre-alpha

### 第四轮（2026-05-24 · Claude Code）— v1.0 正式版

**核心功能补全：**
- 章节连续滚动：全书段落平铺单列表，滚动自动跟踪当前章节
- EPUB 内嵌图片渲染：提取图片到本地，`ParagraphSegment` 混合文本+图片模型
- HTML 样式保留：`Spanned.getSpans()` → `AnnotatedString` + `SpanStyle`
- 大文件懒加载：首次解析前 5 章，后台按需加载剩余章节
- 句子级翻译：长按自动提取句子（`. ! ?` 分界），译文跟随在句子后面

**UX 打磨：**
- 书架侧边栏：☰ 收纳全部控制按钮
- 点击逻辑重写：词→翻译、空白→切换 UI，全屏也可翻译
- `BasicText` + `TextLayoutResult` 替换 deprecated `ClickableText`
- 翻译标注色从灰色改为红色 `#E53935`
- 两端对齐 + 连字符 `Hyphens.Auto`

**基础设施：**
- Room/KSP 编译通过
- 单元测试从 6 个扩展到 31 个
- 备份导出兼容 `Uri.fromFile()` 格式
- 应用图标：Material You 自适应图标
- README / CHANGELOG 文档

**产物：v1.0.0**

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.3.20 |
| UI 框架 | Jetpack Compose + Material 3 | BOM 2026.03.01 |
| 构建 | Gradle + AGP | 8.x / 9.0.1 |
| 架构 | MVVM | AndroidViewModel + StateFlow |
| EPUB 解析 | epub4j-core | 4.2.3 |
| 序列化 | kotlinx.serialization-json | 1.9.0 |
| 数据库 | Room + KSP | 2.8.4 / 2.3.8 |
| 安全 | AndroidX Security Crypto | 1.1.0 |
| 导航 | AndroidX Navigation 3 | 1.0.1 |
| 最低 API | Android 14 | API 34 |

---

## 快速开始

### 环境

- JDK 17
- Android SDK 36
- Android 14+ (API 34+) 设备

### 构建与安装

```bash
git clone https://github.com/Mengchuan-Yang/EngReader-Best.git
cd EngReader-Best/epub-reader-app
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 配置 AI 翻译

书架 ☰ → API Settings → 选择 Provider → 填入 API Key → Save

| Provider | 获取 Key |
|----------|---------|
| DeepSeek | https://platform.deepseek.com/api_keys |
| Gemini | https://aistudio.google.com/apikey |
| OpenAI | https://platform.openai.com/api-keys |

---

## 项目结构

```
EngReader-Best/
├── README.md
├── CHANGELOG.md
└── epub-reader-app/
    ├── build.gradle.kts                 # 根构建脚本
    ├── settings.gradle.kts              # 仓库配置 + Gradle 插件
    ├── gradle/
    │   ├── libs.versions.toml           # 版本目录（统一依赖管理）
    │   └── wrapper/
    ├── docs/                            # 开发文档与交接说明
    └── app/
        ├── build.gradle.kts             # 应用构建脚本
        └── src/
            ├── main/
            │   ├── AndroidManifest.xml
            │   ├── res/
            │   │   ├── values/strings.xml
            │   │   ├── drawable/        # 自适应图标 (foreground/background/monochrome)
            │   │   └── mipmap-*/        # 传统图标（向后兼容）
            │   └── java/com/engreader/app/
            │       ├── MainActivity.kt              # 唯一 Activity，入口
            │       ├── Navigation.kt                # 导航状态键
            │       ├── model/
            │       │   └── Models.kt                # 全部数据模型
            │       ├── ui/main/
            │       │   ├── MainScreen.kt            # 全部 Compose UI (~1200 行)
            │       │   └── MainScreenViewModel.kt   # 全部状态管理 (~650 行)
            │       ├── storage/
            │       │   ├── EpubTextExtractor.kt     # EPUB 解析核心
            │       │   ├── EpubImportService.kt     # 文件导入
            │       │   ├── AppStateStore.kt         # JSON 持久化
            │       │   └── BackupZipService.kt      # ZIP 备份/恢复
            │       ├── ai/
            │       │   ├── AiProvider.kt            # Provider 枚举
            │       │   ├── FallbackTranslationService.kt  # Fallback 协调
            │       │   ├── NetworkProviderClients.kt      # HTTP 请求封装
            │       │   ├── TranslationContracts.kt  # 请求/响应数据类
            │       │   ├── FallbackPolicy.kt        # 优先级策略
            │       │   └── AiProviderPriorityStrategy.kt
            │       ├── settings/
            │       │   └── SecureApiKeyStore.kt     # EncryptedSharedPreferences
            │       ├── theme/
            │       │   ├── Color.kt                 # 色彩定义
            │       │   ├── Theme.kt                 # Material 3 主题
            │       │   └── Type.kt                  # 字体排版
            │       ├── data/local/                  # Room Entity + DAO
            │       ├── data/repository/             # Room Repository 实现
            │       └── domain/                      # Domain 模型 + Repository 接口
            └── test/
                └── java/.../MainScreenViewModelTest.kt  # 31 个单元测试
```

---

## 许可证

MIT License

---

## 致谢

- [epub4j](https://github.com/psiegman/epub4j) — Java EPUB 解析库
- [Jetpack Compose](https://developer.android.com/compose) — Android 声明式 UI 框架
- DeepSeek / Google Gemini / OpenAI — AI 翻译 API
