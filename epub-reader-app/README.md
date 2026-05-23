# EngReader Best

Android EPUB 阅读器 — 支持多 AI 翻译引擎的英文原版书阅读工具。

> **状态**: Pre-Alpha · 功能可用但尚未完善 · 仍在积极开发中

## 功能

- 导入 EPUB 电子书，自动解析章节/目录
- **垂直滚动** 与 **左右翻页** 双阅读模式
- 点击英文单词即时翻译、长按句子翻译
- **DeepSeek / Gemini / OpenAI** 三路翻译后端，支持 fallback 容错
- 翻译结果自动标注，支持单词级和句子级注释
- Material Design 3 阅读界面：沉浸式全屏 ↔ 控制栏一键切换
- 字体大小、行距、段落间距、页边距可调节
- 日间/夜间/系统 三套配色方案
- 书架支持网格/列表视图、阅读进度百分比、书籍封面提取
- 书签、全文搜索、目录跳转
- 备份/恢复（ZIP 格式，含阅读进度和注释）

## 技术栈

| 层 | 技术 |
|---|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | AndroidViewModel + StateFlow |
| EPUB 解析 | epub4j |
| 持久化 | Room (JSON 文件存储) + EncryptedSharedPreferences |
| 翻译 | DeepSeek / Gemini / OpenAI API（多 provider fallback） |
| 构建 | Kotlin 2.3 + AGP 9.0 + Gradle |

## 构建

```bash
cd epub-reader-app
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

要求：
- Android SDK 36
- JDK 17
- Gradle 8.x（wrapper 已包含）

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

最低 Android 14 (API 34)。

## 使用说明

1. **导入书籍**：书架页右下角 + 按钮，选择 .epub 文件
2. **开始阅读**：点击书籍卡片进入阅读页（默认全屏无干扰）
3. **唤出菜单**：点击正文区域弹出底部控制栏
4. **查词翻译**：菜单可见时点击单词翻译，长按句子翻译
5. **调节样式**：底部栏 → ⚙ Style → 弹窗调节字号/行距/段距/边距
6. **切换翻页**：底部栏左侧 ⇄ Paged / ↕ Scroll
7. **返回书架**：侧滑返回 或 顶部栏 ‹ Bookshelf
8. **配置翻译**：书架页 → API Settings → 选择 Provider → 填入 Key

## 项目结构

```
epub-reader-app/
├── app/src/main/java/com/engreader/app/
│   ├── ai/              # 翻译 API 客户端与 fallback 策略
│   ├── data/
│   │   └── repository/  # Room 仓储
│   ├── model/           # 数据模型
│   ├── settings/        # API Key 安全存储
│   ├── storage/         # EPUB 导入/解析/封面提取
│   ├── theme/           # Material 3 主题与色彩
│   └── ui/main/         # 主界面 (MainScreen, ViewModel)
├── app/src/main/res/    # 字符串资源
├── docs/                # 开发文档与交接记录
└── gradle/              # Gradle wrapper 与版本目录
```

## 已知问题 (Pre-Alpha)

- 章节间无法连续滚动，读完一章需手动切换到下一章
- EPUB 内嵌图片暂不支持渲染
- PAGED 模式不支持点按查词（仅长按翻译）
- 部分 EPUB 文件封面提取可能失败
- 无单元测试覆盖

## 版本

**v0.1.0-pre-alpha** — 首次可运行构建

核心阅读 + 翻译闭环可用，UI 交互仍有优化空间。
