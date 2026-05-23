# EngReader Best

> Android EPUB 英文原版书阅读器 — 集成多 AI 翻译引擎 | Pre-Alpha

[![Release](https://img.shields.io/github/v/release/Mengchuan-Yang/EngReader-Best?label=latest)](https://github.com/Mengchuan-Yang/EngReader-Best/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2014%2B-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/compose-Material%203-purple)](https://developer.android.com/compose)

---

## 项目简介

EngReader Best 是一款面向英语学习者和原版书读者的 Android EPUB 阅读器。核心特色是 **点击即译**——阅读英文书籍时，点按任意单词即可获得 AI 驱动的中文释义，长按句子即可整句翻译，翻译结果自动标注在原文旁边。

项目支持 **DeepSeek、Gemini、OpenAI** 三家 AI 翻译后端，具备自动 fallback 容错机制，任一 provider 不可用时自动切换下一个。

当前版本为 **Pre-Alpha**，核心阅读与翻译闭环已可用，UI/UX 仍在迭代优化中。

---

## 功能特性

### 阅读体验
- **双阅读模式**：垂直滚动（适合手机单手握持）+ 左右翻页（模拟纸质书体验）
- **沉浸式全屏**：阅读时 chrome 自动隐藏，点击正文唤出控制栏
- **Material Design 3**：遵循 Google 最新设计规范，日间/夜间/系统三套配色
- **阅读样式调节**：字体大小、行距、段落间距、页边距均可独立调整
- **书籍封面**：自动提取 EPUB 内嵌封面图片，无封面时显示默认书标

### AI 翻译
- **点按查词**：单击英文单词 → AI 结合上下文给出 1-2 个最准确中文释义
- **长按翻句**：长按段落 → AI 结合上下文翻译整句
- **多 Provider**：DeepSeek / Gemini / OpenAI 三路后端
- **Fallback 容错**：按优先级链自动切换，单点故障不影响使用
- **翻译标注**：单词级和句子级翻译结果自动标注在段落中，可删除
- **重复模式**：翻译一个词后，可选择整章 / 全书自动标注所有出现位置

### 书架管理
- **网格 / 列表视图**切换
- **排序**：最近阅读 / 导入时间 / 书名
- **阅读进度**：百分比显示（需至少打开过一次以解析章节数）
- **备份 / 恢复**：ZIP 格式，含书籍、进度、书签、翻译标注

### 其他
- **书签**：一键添加 / 删除
- **全文搜索**：在当前书中搜索关键词并跳转
- **目录跳转**：TOC 弹窗快速切换章节
- **安全存储**：API Key 使用 Android EncryptedSharedPreferences 加密

---

## 截图

> 截图将在后续版本中补充。

---

## 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Ladybug (2024.2+) 或任意支持 AGP 9.0 的 IDE |
| JDK | 17 |
| Android SDK | 36 (Android 14) |
| Gradle | 8.x（项目自带 wrapper） |
| Kotlin | 2.3.20 |
| 目标设备 | Android 14+ (API 34+) |

### 构建

```bash
git clone https://github.com/Mengchuan-Yang/EngReader-Best.git
cd EngReader-Best/epub-reader-app
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

### 安装到手机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或直接从 [Releases](https://github.com/Mengchuan-Yang/EngReader-Best/releases) 下载 APK。

---

## 使用指南

### 1. 导入书籍
书架页面右下角 **+** 按钮 → 选择 .epub 文件 → 自动导入

### 2. 开始阅读
点击书架上的书籍卡片 → 进入阅读页（默认全屏无干扰模式）

### 3. 唤出菜单
**点击正文区域** → 底部控制栏弹出

### 4. 翻译操作
- **查词**：控制栏可见时，点击英文单词 → AI 翻译
- **翻句**：长按段落 → AI 翻译整句
- 翻译结果以灰色小字标注在原文旁边

### 5. 调节阅读样式
底部栏 → **⚙ Style** → 弹窗中分别调节：
- 字体大小 (0.8x ~ 2.0x)
- 行间距 (1.1x ~ 2.2x)
- 段落间距 (0.5x ~ 3.0x)
- 页边距 (0.5x ~ 3.0x)

### 6. 切换翻页模式
底部栏左侧按钮 → **⇄ Paged**（左右翻页）/ **↕ Scroll**（上下滚动）

### 7. 配置 AI 翻译
书架页面 → API Settings → 选择 Provider → 填入 API Key → Save

| Provider | 获取 Key |
|----------|---------|
| DeepSeek | https://platform.deepseek.com/api_keys |
| Gemini | https://aistudio.google.com/apikey |
| OpenAI | https://platform.openai.com/api-keys |

### 8. 导航
- **返回书架**：侧滑返回，或顶部栏 `‹ Bookshelf` 按钮
- **翻页模式**：屏幕左右边缘半透明箭头 `<` `>` 翻页
- **目录跳转**：底部栏 TOC 按钮

---

## 项目结构

```
EngReader-Best/
├── README.md                          # 本文件
├── CHANGELOG.md                       # 变更日志
├── .gitignore
├── .editorconfig
└── epub-reader-app/                   # Android 项目根目录
    ├── README.md                      # 开发者文档
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle/
    │   ├── libs.versions.toml         # 版本目录
    │   └── wrapper/
    ├── gradlew / gradlew.bat
    ├── docs/                          # 开发文档
    │   ├── REQUIREMENTS.md
    │   ├── MILESTONES.md
    │   ├── HANDOFF_FREEZE_2026-05-23.md
    │   ├── HANDOFF_FREEZE_2026-05-23_2.md
    │   ├── CLAUDE_TAKEOVER_INSTRUCTIONS.md
    │   └── DEEPSEEK_TAKEOVER_INSTRUCTIONS.md
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/engreader/app/
            │   ├── MainActivity.kt           # 入口 Activity
            │   ├── Navigation.kt             # 导航（屏幕切换）
            │   ├── ai/                       # 翻译 API 客户端
            │   │   ├── AiProvider.kt
            │   │   ├── AiProviderPriorityStrategy.kt
            │   │   ├── FallbackPolicy.kt
            │   │   ├── FallbackTranslationService.kt
            │   │   ├── NetworkProviderClients.kt
            │   │   ├── PlaceholderProviderClients.kt
            │   │   ├── ProviderTranslationClient.kt
            │   │   ├── TranslationContracts.kt
            │   │   └── TranslationServices.kt
            │   ├── data/                      # Room 数据层
            │   ├── domain/                    # 领域模型与仓储接口
            │   ├── model/Models.kt            # 核心业务模型
            │   ├── settings/                  # API Key 加密存储
            │   │   ├── AiProviderSettings.kt
            │   │   └── SecureApiKeyStore.kt
            │   ├── storage/                   # EPUB 解析与持久化
            │   │   ├── AppStateStore.kt
            │   │   ├── BackupZipService.kt
            │   │   ├── EpubImportService.kt
            │   │   └── EpubTextExtractor.kt
            │   ├── theme/                     # Material 3 主题
            │   │   ├── Color.kt
            │   │   ├── Theme.kt
            │   │   └── Type.kt
            │   └── ui/main/                   # 主界面 (Compose)
            │       ├── MainScreen.kt          # 书架 + 阅读器 UI
            │       └── MainScreenViewModel.kt # 状态管理
            └── res/
                ├── values/strings.xml
                └── values-zh-rCN/strings.xml
```

---

## 开发历史

本项目由 AI 代理接力开发，共经历三轮迭代：

### 第一轮（2026-05-23 上午）
- 初始项目搭建：Gradle 构建、Compose UI 骨架、epub4j 集成
- DeepSeek API 翻译客户端
- 基础书架和阅读页
- **已知问题**：书名乱码、色彩不全、edge-to-edge 未完成、分页算法有误、TOC 乱码、暗色模式不可读

### 第二轮（2026-05-23 下午 · Codex/GPT-5）
- 修复书名/TOC 乱码（`EpubTextExtractor.looksLikeHashOrId`）
- 完善 Material 3 色彩方案（8 色完整 darkColorScheme）
- Edge-to-edge 沉浸式修复（`enableEdgeToEdge` 透明色值）
- 分页算法重构（`maxCharsPerPage` 基于屏幕和字体动态计算）
- 阅读页交互重构（chrome 可见性、TopAppBar 配色）
- **产物**：可构建 APK，但真机验收发现 UI 交互问题

### 第三轮（2026-05-23 晚间 · Claude Code）
- BackHandler 侧滑返回逻辑（阅读页 → 书架 → 退出）
- 点击正文切换 chrome 修复（事件消费冲突）
- Material Design 3 完整重构：BottomBar 控制栏、FAB 导入、Style 弹窗
- 段落间距/页边距调节功能
- 翻译 API 主线阻塞修复（`withContext(Dispatchers.IO)`）
- PAGED 模式卡封面修复（浮动翻页箭头）
- 书架阅读进度百分比、封面图片提取
- 模型名修正（DeepSeek `deepseek-chat`、OpenAI `gpt-4o-mini`）
- 阅读样式调节重构：从 8 按钮挤一行 → 弹窗分级调节
- **产物**：当前版本 `v0.1.0-pre-alpha`

---

## 路线图

### v0.2.0 (Alpha)
- [ ] 章节间连续滚动（无需手动切换）
- [ ] EPUB 内嵌图片渲染
- [ ] PAGED 模式支持点按查词
- [ ] 首次打开自动后台提取封面

### v0.3.0 (Beta)
- [ ] 单元测试覆盖核心逻辑
- [ ] 阅读统计（阅读时长、进度可视化）
- [ ] 词汇本（收藏已翻译单词）
- [ ] 导出翻译标注为 Anki / CSV

### v1.0.0 (Stable)
- [ ] 正式 Material You 动态取色
- [ ] 无障碍适配
- [ ] 平板横屏双页布局
- [ ] Google Play 上架

---

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| UI 框架 | Jetpack Compose + Material 3 |
| 架构模式 | MVVM (AndroidViewModel + StateFlow) |
| EPUB 解析 | [epub4j](https://github.com/psiegman/epub4j) 4.2.3 |
| 数据持久化 | kotlinx.serialization JSON 文件 + Room 数据库 |
| 安全存储 | AndroidX Security Crypto (EncryptedSharedPreferences) |
| 网络请求 | HttpURLConnection + kotlinx.coroutines |
| 构建工具 | Gradle 8.x + AGP 9.0 + Kotlin 2.3 |

---

## 贡献

本项目目前为个人项目，欢迎提 Issue 和 PR。

### 代码规范
- Kotlin 代码遵循 [Google Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- Compose 组件优先使用 Material 3 API
- 翻译 Provider 新增需实现 `ProviderTranslationClient` 接口并注册到 `FallbackTranslationService`

---

## 许可证

MIT License

---

## 致谢

- [epub4j](https://github.com/psiegman/epub4j) — Java EPUB 解析库
- [Jetpack Compose](https://developer.android.com/compose) — Android 现代 UI 工具包
- DeepSeek / Google Gemini / OpenAI — AI 翻译后端
