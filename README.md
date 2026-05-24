# EngReader Best

> Android EPUB 英文原版书阅读器 — 集成多 AI 翻译引擎 | v1.0

[![Release](https://img.shields.io/github/v/release/Mengchuan-Yang/EngReader-Best?label=latest)](https://github.com/Mengchuan-Yang/EngReader-Best/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2014%2B-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/compose-Material%203-purple)](https://developer.android.com/compose)

---

## 项目简介

EngReader Best 是一款面向英语学习者和原版书读者的 Android EPUB 阅读器。核心特色是 **点击即译**——阅读英文书籍时，点按任意单词即可获得 AI 驱动的中文释义，长按句子即可整句翻译，翻译结果自动标注在原文旁边。

项目支持 **DeepSeek、Gemini、OpenAI** 三家 AI 翻译后端，具备自动 fallback 容错机制。

---

## 功能特性

### 阅读体验
- **连续滚动**：全书章节无缝衔接，无需手动切换章节
- **沉浸式全屏**：点击段落空白区或章节标题切换 UI 显示/隐藏
- **Material Design 3**：日间/夜间/系统三套配色，自适应图标
- **阅读样式调节**：字体大小、行距、段落间距、页边距、两端对齐/左对齐
- **两端对齐 + 连字符**：对齐模式下自动断词加 `-` 换行，排版均匀
- **EPUB 内嵌图片**：插图、图表直接渲染在正文中
- **HTML 样式保留**：粗体、斜体、下划线、上下标正确显示
- **懒加载**：大文件打开只解析前 5 章，滚动时按需加载剩余章节
- **书籍封面**：导入时自动提取封面，无需打开书籍

### AI 翻译
- **点按查词**：单击英文单词 → AI 结合上下文给出准确中文释义（全屏模式下也生效）
- **长按翻句**：长按自动提取当前句子 → AI 翻译（非整段），译文跟随在句子后面
- **多 Provider**：DeepSeek / Gemini / OpenAI 三路后端
- **Fallback 容错**：按优先级链自动切换
- **翻译标注**：红色小字标注在原文旁边，可删除
- **重复模式**：翻译一个词后，可选择整章 / 全书自动标注

### 书架管理
- **侧边栏菜单**：点击 ☰ 展开，包含视图切换、排序、主题、API 设置、备份/还原
- **网格 / 列表视图**
- **排序**：最近阅读 / 导入时间 / 书名
- **阅读进度**百分比显示
- **备份 / 恢复**：ZIP 格式，含书籍、进度、书签、翻译标注

### 其他
- **书签**：一键添加 / 删除
- **全文搜索**：关键词搜索并跳转
- **目录跳转**：TOC 弹窗快速切换章节
- **安全存储**：API Key 使用 Android EncryptedSharedPreferences 加密
- **Room 数据库**：注解处理器已配置，为后续数据迁移做好准备

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

---

## 使用指南

### 1. 导入书籍
书架右下角 **+** → 选择 .epub → 自动导入并提取封面

### 2. 开始阅读
点击书籍卡片 → 进入阅读页（默认全屏无干扰模式）

### 3. 唤出菜单
- 点击段落中的标点或空白区域 → 切换 UI
- 点击章节标题分隔符 → 切换 UI
- 系统返回键也可切换

### 4. 翻译操作
- **查词**：直接点击英文单词 → AI 翻译（全屏模式下同样可用）
- **翻句**：长按段落中的句子 → 自动提取当前句子并翻译
- 翻译结果以红色小字标注在原词/原句旁边

### 5. 调节阅读样式
底部栏 → **⚙ Style** → 弹窗中调节：
- 字体大小 (0.8x ~ 2.0x)
- 行间距 (1.1x ~ 2.2x)
- 段落间距 (0.5x ~ 3.0x)
- 页边距 (0.5x ~ 3.0x)
- 排版方式：两端对齐 / 左对齐

### 6. 配置 AI 翻译
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
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle/
    │   └── libs.versions.toml
    └── app/src/main/
        ├── AndroidManifest.xml
        └── java/com/engreader/app/
            ├── MainActivity.kt
            ├── ai/                  # 翻译 API 客户端
            ├── data/                # Room 数据层
            ├── domain/              # 领域模型与仓储接口
            ├── model/Models.kt      # 核心业务模型
            ├── settings/            # API Key 加密存储
            ├── storage/             # EPUB 解析与持久化
            ├── theme/               # Material 3 主题
            └── ui/main/             # 主界面 (Compose)
```

---

## 许可证

MIT License

---

## 致谢

- [epub4j](https://github.com/psiegman/epub4j) — Java EPUB 解析库
- [Jetpack Compose](https://developer.android.com/compose) — Android 现代 UI 工具包
- DeepSeek / Google Gemini / OpenAI — AI 翻译后端
