# EPUB 读取到渲染链路 Review 报告（2026-05-24）

## 0. 说明

本报告基于当前工程实际代码状态审查，项目在交接后已被其他智能体多轮修改，因此本报告不以旧交接文档路径为准，而以当前真实代码为准。

审查范围：EPUB 从文件导入、ZIP/EPUB 结构、container.xml、OPF manifest/spine、XHTML 解码、图片/CSS 路径、Compose 渲染层的完整链路。

本报告只列问题与修复建议，不包含代码修改。

## 1. 总体结论

当前实现可以读取部分 EPUB 并将 XHTML 降级成纯文本/图片片段显示，但还不是完整稳定的 EPUB 渲染链路。

核心风险集中在三处：

1. 导入阶段只做 ZIP 头部弱校验，没有验证 EPUB 必需结构。
2. EPUB 结构解析高度依赖 epub4j 黑盒，缺少 container/OPF/manifest/spine 的可诊断层。
3. XHTML/CSS/图片/链接在进入 Compose 前被大量降级，导致目录、标题、图片、CSS、内部链接、分页都容易失真。

## 2. 当前关键代码入口

- 导入复制与基础校验：
  - `app/src/main/java/com/engreader/app/storage/EpubImportService.kt`
- EPUB 元数据、章节、图片、TOC 抽取：
  - `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt`
- 阅读页 UI 与渲染：
  - `app/src/main/java/com/engreader/app/ui/main/MainScreen.kt`
- 阅读页状态与打开书籍流程：
  - `app/src/main/java/com/engreader/app/ui/main/MainScreenViewModel.kt`
- 主题/夜间模式：
  - `app/src/main/java/com/engreader/app/theme/Theme.kt`
  - `app/src/main/java/com/engreader/app/theme/Color.kt`

## 3. 分层 Review

### 3.1 ZIP 解压/读取

现状：

- `EpubImportService.importFromUri()` 复制用户选择的文件到 app 内部目录。
- 只检查文件开头是否类似 ZIP：`PK`。
- 没有真正验证 EPUB 结构。

风险点：

- 普通 ZIP 文件只要以 `PK` 开头也会通过。
- 没检查 EPUB 必需的 `mimetype` 文件。
- 没检查 `META-INF/container.xml` 是否存在。
- 没检查 OPF 文件是否存在、是否可解析。
- 使用 `InputStream.available()` 判断文件大小不可靠，很多 ContentProvider 返回值不代表真实文件长度。

建议：

- 使用 `ZipFile` 或复制后读取本地文件校验 EPUB。
- 必须检查：
  - `mimetype` 存在且内容为 `application/epub+zip`
  - `META-INF/container.xml` 存在
  - container.xml 指向的 OPF 路径存在
- 文件大小建议使用复制后的 `destFile.length()` 或 ContentResolver 的 `OpenableColumns.SIZE`。

优先级：P0。

### 3.2 container.xml 定位 OPF

现状：

- 项目代码没有显式解析 `META-INF/container.xml`。
- 完全依赖 `EpubReader().readEpub(stream)`。

风险点：

- container.xml 异常时，应用只能表现为打开失败、无章节、标题乱码或空内容。
- 无法判断 OPF base path，后续图片/CSS/link 相对路径解析缺少可靠基准。
- 无法处理或诊断多个 rootfile 的 EPUB。

建议：

- 新增 `EpubStructureInspector` 或类似模块。
- 显式读取 `META-INF/container.xml`，提取 rootfile `full-path`。
- 保存并暴露：
  - OPF 路径
  - OPF base directory
  - rootfile media-type
  - container 解析错误信息

优先级：P0。

### 3.3 OPF manifest 和 spine 解析

现状：

- 章节加载使用 `book.spine.spineReferences`。
- HTML 判断主要看 href 后缀是否为 `.xhtml/.html/.htm`。
- 图片资源通过 spine resource、coverImage、以及反射读取 book.resources。

风险点：

- 如果 manifest 里 media-type 正确但 href 后缀不规范，章节可能被漏掉。
- 图片资源读取依赖反射，容易受 epub4j 内部实现变化影响。
- 没有显式处理 `linear="no"`、nav document、NCX、guide 等结构。
- 目录和 spine 之间只有 href 简单归一化匹配，fragment/id 级别标题会丢。

建议：

- 以 OPF manifest 的 `media-type` 作为资源类型判断主依据，href 后缀只作辅助。
- 显式解析 manifest item：
  - id
  - href
  - media-type
  - properties
- 显式解析 spine itemref：
  - idref
  - linear
  - 对应 manifest item
- TOC 标题映射需要同时支持：
  - `chapter.xhtml`
  - `chapter.xhtml#anchor`
  - URL decoded path
  - OPF base path 下的规范化路径

优先级：P0。

### 3.4 XHTML 章节按 spine 顺序加载

现状：

- 当前章节顺序基本来自 `book.spine.spineReferences`。
- `parseBook()` 和 `preRenderAll()` 对空章节的索引处理不一致。

风险点：

- `parseBook()` 中如果 `rawHtml.isBlank()`，会 `continue`，并且不递增 chapterIndex。
- `preRenderAll()` 中如果 `rawHtml.isBlank()`，会递增 chapterIndex。
- 这会造成章节索引不稳定，影响：
  - 目录跳转
  - 书签
  - 阅读进度
  - 注释定位
  - 后台预渲染合并

建议：

- 使用稳定的 `spineIndex`，每个 spine HTML item 都应分配固定 index。
- 空章节也应生成占位或记录解析失败，不应悄悄跳过导致 index 漂移。
- 背景加载章节时，合并逻辑应以稳定 spineIndex 为准。

优先级：P0。

### 3.5 文本编码解码

现状：

- XHTML 内容通过 `resource.reader.readText()` 读取。
- 没有显式读取 BOM、XML declaration、meta charset 或 content-type charset。

风险点：

- 对 UTF-8 规范 EPUB 通常可用，但中文旧 EPUB、非标准 EPUB、GBK/GB18030 内容可能乱码。
- reader 失败被 `runCatching { ... }.getOrDefault("")` 静默吞掉，导致问题表现为“空章节”或“目录/标题异常”。

建议：

- 优先读取 resource 原始 bytes。
- 编码判断顺序建议：
  - BOM
  - XML declaration：`<?xml encoding="...">`
  - HTML meta charset
  - Content-Type charset
  - UTF-8 默认
  - 必要时回退 GB18030/GBK
- 不要静默吞异常；至少记录 href、异常类型、fallback 编码。

优先级：P0。

### 3.6 图片和 CSS 相对路径解析

现状：

- 图片通过正则替换 `<img src="...">` 为内部 marker：`[[IMG:path|alt]]`。
- 图片路径匹配使用多种启发式方式，例如 simpleName、`../Images/xxx`、endsWith。
- CSS 基本被删除，`<style>` 会在 HTML 转文本前移除。
- `extractCssImages()` 存在但未实际接入有效链路。

风险点：

- 相同文件名在不同目录下会撞名，可能显示错图。
- 相对路径不是按“当前 XHTML 文件路径 + OPF base path”解析，复杂 EPUB 图片容易丢。
- CSS 中的背景图、宽高、居中、缩进、display、字体样式会丢失。
- SVG 图片被识别为 image，但后续 `BitmapFactory.decodeFile()` 可能无法渲染 SVG。

建议：

- 建立统一 `resolveResourceHref(baseHref, relativeHref)`：
  - 使用 OPF base directory
  - 使用当前 XHTML resource href 的所在目录
  - 支持 `../`
  - 支持 URL decode
  - 去除 fragment
- 图片 map 以规范化绝对 EPUB 内路径为 key，不使用 simpleName 作为主匹配。
- SVG 需要单独处理，不能依赖 BitmapFactory。
- CSS 初期不用完整支持，但至少保留：
  - 图片宽度/高度
  - 居中
  - 段落缩进
  - display none
  - blockquote/list 基础样式

优先级：P1。

### 3.7 渲染层：段落、标题、图片、链接

现状：

- 渲染层使用 Compose `LazyColumn`。
- 文本使用 `BasicText`。
- 标题通过 span/字号推测。
- 图片使用 `BitmapFactory.decodeFile()` 同步解码。
- URLSpan 支持外部链接打开。

风险点：

- 同步解码图片会卡 UI，图片大时有 OOM 风险。
- 内部链接如 `chapter.xhtml#id` 或 `#note1` 没有转为章节内跳转。
- 段落模型过于简单，XHTML block 语义被压扁成字符串列表。
- 列表、表格、blockquote、标题层级、图片说明、脚注都只得到有限处理。
- `ReaderMode.PAGED` 目前没有看到真正独立分页渲染，阅读页仍主要是 `LazyColumn`。

建议：

- 把解析结果从 `paragraphs: List<String>` 升级为 block model：
  - HeadingBlock
  - ParagraphBlock
  - ImageBlock
  - ListBlock
  - BlockQuoteBlock
  - TableBlock 或 TableTextBlock
  - LinkAnnotation
- 图片改为异步加载并限制最大尺寸。
- 内部链接转为 app 内跳转，外部链接才使用 `ACTION_VIEW`。
- 分页模式单独实现，按可见区域、字号、行高、边距测量分页，不能复用滚动列表。

优先级：P0/P1。

## 4. 与当前用户反馈的对应关系

### 4.1 书名乱码/未读取出来

可能根因：

- metadata 解码依赖 epub4j，未做编码 fallback。
- `looksLikeHashOrId()` 会过滤看似 ID 的标题，但 fallback 可能来自文件名或章节首段。
- 旧 EPUB 元数据可能不是 UTF-8。

建议：

- 对 OPF metadata 做 bytes 级别解析与编码 fallback。
- title 候选顺序建议：
  - OPF dc:title
  - nav/toc 书名
  - 第一章有效 h1/title
  - 文件名

### 4.2 目录乱码

可能根因：

- TOC 标题来自 epub4j 的 `book.tableOfContents`，同样可能编码异常。
- TOC href 与 spine href 只做简单 normalize，fragment 标题可能匹配失败。

建议：

- 显式解析 EPUB3 nav.xhtml 和 EPUB2 toc.ncx。
- TOC item 保留 href + fragment。
- spine 章节标题优先使用精确 href/fragment 匹配。

### 4.3 左右翻页一页只显示一段

可能根因：

- 当前代码中 `ReaderMode.PAGED` 没有对应渲染分支。
- 如果其他智能体新增过分页，需要重点检查其是否按段落分页，而不是按屏幕高度分页。

建议：

- 建立分页引擎，基于 Compose TextMeasurer 或等价测量策略。
- 分页单位应该是“可见区域容纳的行/块”，不是固定一个段落。

### 4.4 夜间模式文字看不清

当前颜色文件中暗色 `onBackground` 已是浅色，但仍需检查：

- styled span 中 `ForegroundColorSpan` 是否强制写入黑色。
- HTML/CSS 颜色是否覆盖正文颜色。
- 弹窗/Surface 是否使用正确 onSurface。

建议：

- 夜间模式下忽略或重映射 XHTML 中过暗的前景色。
- 对正文统一应用 reader content color，只有链接/注释等特殊元素使用可读色。

## 5. 建议修复顺序

1. 新增 EPUB 结构检查层：ZIP、mimetype、container.xml、OPF path。
2. 显式解析 OPF manifest/spine，并输出调试日志。
3. 修复章节索引稳定性，统一 parseBook/preRenderAll/parseChapterRange。
4. 修复 XHTML bytes 级解码，增加 UTF-8/GB18030 fallback。
5. 修复 TOC/nav/ncx 标题解析和 href+fragment 映射。
6. 修复图片相对路径解析，去掉 simpleName 作为主匹配。
7. 渲染模型升级为 block model。
8. 分页模式单独实现，不复用滚动列表。
9. 夜间模式下重映射 HTML 前景色，保证可读。

## 6. 建议新增测试

至少添加以下本地测试资源与测试：

1. 标准 UTF-8 EPUB：验证 mimetype/container/OPF/spine。
2. 中文 UTF-8 EPUB：验证 dc:title、nav、章节正文。
3. GBK/GB18030 中文 EPUB：验证编码 fallback。
4. 图片相对路径 EPUB：验证 `../Images/a.jpg`、`images/a.jpg`、URL encoded 路径。
5. TOC fragment EPUB：验证 `chapter.xhtml#section1` 跳转和标题匹配。
6. CSS 基础样式 EPUB：验证 display none、居中图片、blockquote、列表。
7. 空章节或非 linear spine EPUB：验证章节 index 稳定。

## 7. 给下一个智能体的执行要求

- 先补诊断层，再修 UI 表现。
- 每修一层都要写明：
  - 问题
  - 根因
  - 修改文件
  - 验证方式
  - 是否影响已有阅读进度/书签/注释
- 不要用大规模重构一次性替换整条链路。
- 不要继续扩大正则解析 XHTML 的范围；结构化 XML/HTML 解析优先。
- 任何失败不要静默吞掉，至少保留 href 和错误类型。

