# Changelog

All notable changes to EngReader Best.

---

## [1.0.2] — 2026-05-24

### 数据链路完整性修复（P0）

基于 EPUB 二次审计报告的全量修复。

**章节索引与懒加载**
- 修复 `parseBook()` 章节 index 双重递增 bug（每项 spine item 递增加上末尾 `chapterIndex += 1`）
- 新增 `ParsedBook.totalChapters` 字段传递 spine 真实章节总数
- ViewModel `openBook()` 改为固定长度 placeholder 列表，后台加载按 index 正确合并
- 空章节生成占位 `ChapterContent`，不再导致索引缺项

**container.xml 解析**
- `EpubStructureInspector.checkContainer()` 正则→XML Pull Parser 重写
- 新增 `Rootfile` 数据结构，按 `media-type` 正确选择 OPF
- 路径 URL decode + 规范化

**OPF manifest/spine 显式解析**
- 新增 `OpfInfo`、`ManifestItem`、`SpineItemRef` 数据结构
- 新增 `readOpfInfo()` 方法，XML 解析 metadata/manifest/spine
- metadata fallback 从 ZipFile 真实 OPF path 读取，不再依赖 spine 中的错误路径

**编码修复**
- GB2312/GBK/GB18030 改为 `Charset.forName("GB18030")` 真实解码
- 新增 `decodeStrict()` 严格 UTF-8 检测（`CodingErrorAction.REPORT`）
- 解码链：BOM→XML encoding→meta charset→strict declared→strict UTF-8→GB18030→ISO-8859-1
- `readMetadataSafe()` 接入 OPF bytes 解码 fallback

**图片/CSS 路径解析**
- 新增 `resolveEpubHref(opfBaseDir, currentResourceHref, relativeHref)` 归一化路径解析
- `replaceImgTags()` 接入 resourceHref + opfBaseDir，路径解析不再靠 simpleName 猜测
- `extractCssImages()` 接入解析链路

**渲染层**
- PAGED 模式用 `HorizontalPager` 独立实现（章节级翻页）
- 内部链接 vs 外部链接区分
- SVG/图片失败有文本占位
- 段首缩进全部移除

---

## [1.0.1] — 2026-05-24

## [1.0.1] — 2026-05-24

### 管道完整性修复

对 EPUB 从文件到屏幕的 7 层渲染管道进行全面审计和加固。

### 新增
- **预渲染机制**：导入 EPUB 后后台解析全部章节，书架卡片显示进度条。5 分钟超时防止无限渲染。存量书籍不受影响。
- **文件完整性校验**：导入前检查文件大小（>500MB 拒绝）+ ZIP magic bytes 验证
- **OPF manifest 全量访问**：通过 Java 反射访问 epub4j `Book.resources` 私有字段，图片不再仅限 spine 中声明的资源
- **HTML 结构渲染**：标题（dp 阈值检测 + Bold）、链接（蓝色下划线 + 可点击打开浏览器）、块引用（fillMaxHeight 竖条 + 斜体）、列表（• / 1. 2. 3. 前缀）、表格（│ 分隔文本）
- **HTML 样式渲染**：粗体、斜体、下划线、删除线、上下标、等宽字体、前景色完整保留

### 修复
- TOC 章节标题匹配回归 bug（`parseChapterContent` 丢失 `resourceHref`）
- `sanitizeHtmlStyledParagraphs` 重复行匹配错误（`indexOf` → `\n` 位置扫描法）
- 图片路径覆盖不足（3 种 → 10 种 fallback + suffix 通配）
- Blockquote 竖条高度固定不伸展（`height(24.dp)` → `fillMaxHeight() + IntrinsicSize.Min`）
- `AbsoluteSizeSpan` 阈值不适用多密度设备（px → dp 转换）
- 翻译标注颜色灰不可读（`#9CA3AF` → `#E53935` 红色）
- 句子去重粒度太粗（段落级 → 句子文本级 `anchorText` 匹配）

---

## [1.0.0] — 2026-05-24

### 1.0 正式发布

首个稳定版本，完成所有核心功能的开发与打磨。

### 新增
- **应用图标**：深蓝底色 + 打开书本 + 红色书签的 Material You 自适应图标
- **书架侧边栏**：☰ 汉堡菜单收纳所有功能按钮（视图、排序、主题、API、备份、还原）
- **两端对齐排版**：样式对话框新增对齐方式切换，支持左对齐 / 两端对齐
- **连字符断词**：两端对齐模式下，行尾单词自动用 `-` 断开换行，避免排版过疏
- **句子级翻译**：长按自动提取当前句子（以 `. ! ?` 为界），译文跟随在原句后面
- **全屏翻译**：即使 UI 完全隐藏，点击单词仍可触发翻译
- **导入时提取封面**：书籍导入后立即提取封面，无需先打开
- **KSP/Room**：KSP 注解处理器已配置，Room 数据库可编译

### 修复
- **HTML 样式保留**：粗体、斜体、下划线、删除线、上下标现在正确渲染
- **章节连续滚动**：全书段落平铺在单个滚动列表中，章节间无停顿
- **EPUB 内嵌图片**：`<img>` 标签图片提取到本地并在正文中渲染
- **大文件懒加载**：打开书籍时只解析前 5 章，滚动时按需加载剩余章节
- **翻译标注颜色**：从灰色 `#9CA3AF` 改为红色 `#E53935`，日间模式清晰可读
- **句子去重粒度**：从段落级改为句子文本级，同一段落不同句子可分别翻译
- **备份导出**：文件路径解析兼容 `Uri.fromFile()` 格式
- **UI 切换逻辑**：点击标点/空白/章节标题切换 UI，点击单词翻译，互不干扰
- **单元测试**：从 6 个扩展到 31 个，覆盖单词提取、CJK 检测、翻译错误映射、标注渲染、偏移量映射

### 已知限制
- epub4j 库不支持流式读取，超大 EPUB 的内存占用仍较高
- Room 数据库已可编译但尚未接入 ViewModel（仍使用 JSON 存储）
- UI 层和 EPUB 解析层的集成测试待补充

---

## [0.1.0-pre-alpha] — 2026-05-23

### Pre-Alpha 基线

- 初始项目搭建：Gradle 构建、Compose UI 骨架、epub4j 集成
- DeepSeek / Gemini / OpenAI 三路 AI 翻译后端
- 基础书架（网格/列表）+ 阅读器（垂直滚动）
- Material Design 3 日间/夜间/系统主题
- 阅读样式调节：字体大小、行距、段落间距、页边距
- 书签、全文搜索、目录跳转
- ZIP 备份/恢复
- API Key 加密存储 (EncryptedSharedPreferences)
- 修复书名/TOC 乱码、暗色模式对比度、分页算法等多个问题
