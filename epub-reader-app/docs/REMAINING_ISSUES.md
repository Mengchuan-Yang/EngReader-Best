# EngReader Best — 待解决问题清单

> 本文档供下一个开发 Session 使用。包含完整的问题描述、技术背景、推荐方案和 Git 操作指南。

---

## 仓库信息

| 项 | 值 |
|---|-----|
| **GitHub** | https://github.com/Mengchuan-Yang/EngReader-Best |
| **本地路径** | `C:\Users\as705\Documents\EPUB READER APK\epub-reader-app` |
| **当前版本** | `v0.1.0-pre-alpha` |
| **当前分支** | `master` |

### 克隆与推送

```bash
# 克隆
git clone https://github.com/Mengchuan-Yang/EngReader-Best.git
cd EngReader-Best/epub-reader-app

# 开发流程
./gradlew assembleDebug                    # 构建
adb install -r app/build/outputs/apk/debug/app-debug.apk  # 安装

# 提交推送
cd ../   # 回到仓库根目录 EngReader-Best/
git add -A
git commit -m "描述改动"
git push origin master
```

### 关键约束（必须遵守）

- **不删除** DeepSeek / Gemini / OpenAI 多 provider 结构
- **不硬编码** API Key
- **每次改动后** `./gradlew assembleDebug` 必须通过
- **不新增**非必要功能
- **优先 Material Design 3** 组件
- **MainScreen.kt** 文件大（~800 行），修改时避免截断
- `app/build.gradle.kts` 中 `namespace = "com.engreader.app"`，源码路径 `com/engreader/app/`

---

## 1. EPUB 内嵌图片无法渲染 🔴 高优先级

### 现象
EPUB 文件中的插图、封面内图、图表等在阅读界面完全不显示。

### 根因
- `EpubTextExtractor.sanitizeHtml()` 使用 `HtmlCompat.fromHtml(...).toString()`，丢弃了所有 `<img>` 标签
- 当前数据模型 `ChapterContent.paragraphs: List<String>` 无法承载图片节点
- Compose 渲染层只有 `ClickableText`，没有 `Image` 插槽

### 推荐方案
1. 在 `parseBook()` 中，不丢弃 `<img>` 标签，而是将其替换为特殊占位标记（如 `[[IMG:resourceId]]`）
2. 将 EPUB 内的图片资源提取并保存到 `context.filesDir/images/` 目录
3. 段落数据模型改为支持混合内容（文本 + 图片节点）
4. 在渲染层使用 `Column` 或 `AnnotatedString` + `InlineContent` 将图片嵌入文本流

### 参考文件
- `EpubTextExtractor.kt` — `parseBook()`, `sanitizeHtml()`
- `MainScreen.kt` — `ReaderContent()` 渲染逻辑
- `Models.kt` — `ChapterContent` 数据模型

---

## 2. 章节间无法连续滚动 🔴 高优先级

### 现象
阅读时读完一章后必须点击 "Next" 按钮才能进入下一章。无法像普通阅读器一样无缝连续滚动。

### 根因
- `MainScreen.kt` 的 `ReaderContent` 中只渲染 `state.currentChapter.paragraphs`
- 读完一章后 LazyColumn 已到底，但没有自动加载下一章的逻辑

### 推荐方案
1. 在 VERTICAL 模式下，将所有章节的段落平铺到单个 LazyColumn 中
2. 每个段落携带其所属的 `chapterIndex`
3. 当 LazyColumn 滚动到新章节的段落时，自动更新 `currentChapterIndex`（触发 ViewModel 更新阅读进度）
4. 在章节边界处插入章节标题分隔符

### 参考文件
- `MainScreen.kt` — `ReaderContent()` 的 LazyColumn 部分
- `MainScreenViewModel.kt` — `setCurrentParagraph()`, `setChapter()`

---

## 3. HTML 排版样式丢失 🟡 中优先级

### 现象
EPUB 中原有的加粗、斜体、下划线、标题层级、文字对齐等 CSS/HTML 样式在阅读界面完全消失，所有文字显示为统一的纯文本样式。

### 根因
- `EpubTextExtractor.sanitizeHtml()` 调用 `HtmlCompat.fromHtml(...).toString()` 丢弃了所有 `Spanned` 样式标记
- `Models.kt` 中 `ChapterContent` 已预留 `styledParagraphs: List<AnnotatedString>` 字段，但解析时未填充

### 推荐方案
1. 新增 `sanitizeHtmlStyled()` 函数，对 `Spanned` 对象遍历 `getSpans()` 提取样式
2. 将 `android.text.style.StyleSpan`（粗体/斜体）转换为 Compose `SpanStyle(fontWeight, fontStyle)`
3. 将 `android.text.style.UnderlineSpan` 转换为 `SpanStyle(textDecoration)`
4. 构建 `AnnotatedString` 并填充到 `ChapterContent.styledParagraphs`
5. `MainScreen.kt` 渲染时优先使用 `styledParagraphs`（如果非空），否则 fallback 到纯文本 `paragraphs`

### 参考文件
- `EpubTextExtractor.kt` — `sanitizeHtml()`, `parseBook()`
- `Models.kt` — `ChapterContent.styledParagraphs`
- `MainScreen.kt` — `ClickableText` 渲染

---

## 4. 打开大文件时首加载卡顿 🟡 中优先级

### 现象
打开字数多或体积大的 EPUB 时，加载圈转数秒到十几秒，UI 完全卡住。

### 根因
- `openBook()` 中 `parseBook()` 一次性解析所有章节的全部段落
- epub4j 使用 `InputStream` 方式读取，将所有资源（图片等）加载到内存

### 推荐方案
1. **按需加载**：`parseBook()` 仅解析 TOC 结构和第一章内容
2. 新增 `parseChapter(chapterIndex)` 方法，在用户滚动到新章节时才解析
3. 在 VERTICAL 模式的 LazyColumn 底部添加 "加载中" 指示器
4. epub4j 限制：该库不支持 `ZipFile` 流式读取，大文件内存问题暂时无法根本解决

### 参考文件
- `EpubTextExtractor.kt` — `parseBook()`
- `MainScreenViewModel.kt` — `openBook()`
- `MainScreen.kt` — `ReaderContent()` 滚动检测

---

## 5. Room 数据库完全闲置 🟡 中优先级

### 现象
项目中有一套设计完整的 Room 数据库架构（Entity, DAO, Database, Repository），但运行时完全不使用。所有数据通过 `AppStateStore` 读写单个 JSON 文件 `engreader_state.json`。

### 根因
- 项目初始阶段为快速开发选择了 JSON 文件方案
- Room 架构是后来添加的，但从未接入 ViewModel 和 UI

### 问题
- JSON 全量序列化在大数据量下效率极低（几千条注释后读写断崖式下降）
- Room 代码（约 20 个文件）成为死代码

### 推荐方案
1. 先确保 Room 可编译：在 `libs.versions.toml` 中已有 `ksp` 和 `room-compiler` 版本定义，需要在 `app/build.gradle.kts` 中 apply KSP 插件并添加 `ksp(libs.androidx.room.compiler)` 依赖。KSP 版本需要与 Kotlin 版本匹配（当前 Kotlin 2.3.20，需找到对应的 KSP 版本）
2. 创建 `AppDatabase` 单例
3. 逐步迁移功能：先迁移 Settings，再迁移 Books，最后迁移 Annotations/Bookmarks
4. 保留 `AppStateStore` 作为过渡期的兼容层

### 参考文件
- `data/local/` — Room Entity 和 DAO
- `data/repository/` — Room Repository 实现
- `domain/repository/` — Repository 接口
- `storage/AppStateStore.kt` — 当前 JSON 存储（待替换）
- `gradle/libs.versions.toml` — KSP/Room 版本定义
- `app/build.gradle.kts` — 需添加 KSP 插件和依赖

---

## 6. 单元测试覆盖不足 🟢 低优先级

### 现象
- 仅 `MainScreenViewModelTest.kt` 中 6 个 `extractWordAtOffset` 纯逻辑测试
- 无 ViewModel、Repository、EPUB 解析的测试
- `./gradlew test` 可通过但覆盖率极低

### 推荐方案
1. 引入 Robolectric 或使用 `AndroidJUnit4` 测试 Android 相关逻辑
2. 为核心逻辑添加测试：`translateWordAt`, `translateSentenceAt`, `parseBook`, `sanitizeHtml`
3. 为 `AppStateStore` 添加集成测试

### 参考文件
- `app/src/test/` — 单元测试目录
- `app/src/androidTest/` — Android 集成测试目录

---

## 7. 封面提取时机过晚 🟢 低优先级

### 现象
新导入的书籍在书架上显示默认图标，只有**打开过一次**后才能显示封面。

### 根因
- 封面提取在 `openBook()` 时异步执行
- 导入时（`importFromUri`）不提取封面

### 推荐方案
在 `importFromUri` 完成后立即调用 `extractCover()`，或者将封面提取移到导入流程中。

### 参考文件
- `MainScreenViewModel.kt` — `importFromUri()`, `openBook()`
- `EpubTextExtractor.kt` — `extractCover()`

---

## 8. 备份导出使用旧 API（待清理） 🟢 低优先级

### 现象
`BackupZipService.exportToZip()` 中尝试通过 `Uri.parse(book.sourceUri).path` 获取文件路径，但 `sourceUri` 已改为 `Uri.fromFile()` 的格式。

### 推荐方案
确认导出/导入流程在私有存储迁移后仍正常工作，添加错误处理。

### 参考文件
- `BackupZipService.kt`

---

## 快速参考：关键文件索引

| 功能 | 文件路径 |
|------|---------|
| 主界面 UI | `app/src/main/java/com/engreader/app/ui/main/MainScreen.kt` |
| 状态管理 | `app/src/main/java/com/engreader/app/ui/main/MainScreenViewModel.kt` |
| 数据模型 | `app/src/main/java/com/engreader/app/model/Models.kt` |
| EPUB 解析 | `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt` |
| EPUB 导入 | `app/src/main/java/com/engreader/app/storage/EpubImportService.kt` |
| 数据持久化 | `app/src/main/java/com/engreader/app/storage/AppStateStore.kt` |
| 备份还原 | `app/src/main/java/com/engreader/app/storage/BackupZipService.kt` |
| 翻译客户端 | `app/src/main/java/com/engreader/app/ai/NetworkProviderClients.kt` |
| 翻译服务 | `app/src/main/java/com/engreader/app/ai/FallbackTranslationService.kt` |
| API Key 存储 | `app/src/main/java/com/engreader/app/settings/SecureApiKeyStore.kt` |
| 主题色彩 | `app/src/main/java/com/engreader/app/theme/Theme.kt` + `Color.kt` |
| 字符串资源 | `app/src/main/res/values/strings.xml` |
| 版本目录 | `gradle/libs.versions.toml` |
| Room 相关 | `app/src/main/java/com/engreader/app/data/` + `domain/` |
| 中文文档 | `README.md`（仓库根）, `docs/`（开发文档） |

---

## 构建命令备忘

```powershell
# 在 epub-reader-app/ 目录下:
./gradlew assembleDebug                              # 构建 APK
./gradlew clean assembleDebug                        # 清理构建
./gradlew testDebugUnitTest                          # 单元测试
adb install -r app/build/outputs/apk/debug/app-debug.apk  # 安装到手机

# 在仓库根目录下:
git add -A
git commit -m "描述"
git push origin master
git tag -a v0.1.1-pre-alpha -m "描述"
git push origin v0.1.1-pre-alpha
```
