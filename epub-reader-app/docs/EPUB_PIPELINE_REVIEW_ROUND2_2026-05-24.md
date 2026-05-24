# EPUB 读取到渲染链路二次 Review 报告（2026-05-24）

## 0. 审查背景

本报告基于 Claude 按上一轮 `EPUB_PIPELINE_REVIEW_2026-05-24.md` 修改后的当前代码进行二次审查。

本次只做 review，不修改业务代码。

审查范围仍然是 EPUB 从文件读取到屏幕渲染的完整链路：

1. ZIP 解压/校验
2. container.xml 定位 OPF
3. OPF manifest 和 spine 解析
4. XHTML 章节按 spine 顺序加载
5. 文本按 UTF-8/声明编码解码
6. 图片和 CSS 相对路径解析
7. 渲染层处理段落、标题、图片和链接

## 1. 总体结论

Claude 这一轮已经明显改善了导入校验和部分解析稳定性：

- 新增了 `EpubStructureInspector.kt`，开始显式检查 ZIP/mimetype/container/OPF。
- 导入阶段改为复制后校验 EPUB 结构。
- XHTML 读取改为优先 `resource.data` 并增加 BOM/XML/meta charset 检测。
- HTML resource 判断开始优先看 media-type。
- TOC map 尝试保留 fragment 映射。
- SVG 图片至少不再直接交给 BitmapFactory 渲染。

但当前仍然不是稳定的完整 EPUB 链路，并且出现了几个新的高风险回归：

- `parseBook()` 中章节索引被递增两次，且 placeholder 写入了错误 index，可能导致章节编号、目录跳转、书签、进度错乱。
- `decodeBytes()` 将 GB2312/GBK/GB18030 声明错误映射到 UTF-8，中文老 EPUB 仍会乱码。
- `readMetadataSafe()` 试图从 spine 中找 `.opf` resource，基本找不到，OPF metadata fallback 实际大概率无效。
- 新增的 `EpubStructureInspector` 仍然用正则解析 XML，且 rootfile media-type 选择逻辑有 bug。
- 图片/CSS 路径仍未按 OPF base + 当前 XHTML 目录解析，仍依赖 simpleName/endsWith 猜测。
- `ReaderMode.PAGED` 仍没有真正渲染分支，阅读页仍是 `LazyColumn`。

建议下一轮优先处理 P0 问题，不要继续叠加 UI 功能。

## 2. P0 必修问题

### P0-1：`parseBook()` 章节索引被二次递增

位置：

- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:115`
- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:123`
- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:140`
- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:143`
- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:150`

现状：

```kotlin
val currentIdx = chapterIndex
chapterIndex++
...
fallback = defaultTitle(chapterIndex)
...
index = chapterIndex
...
chapterIndex += 1
```

风险：

- 每处理一个 HTML spine item，`chapterIndex` 会递增两次。
- placeholder 章节使用的是递增后的 `chapterIndex`，不是 `currentIdx`。
- 章节标题可能变成 Chapter 2、4、6。
- TOC、搜索、书签、注释、进度、后台章节合并全部可能错位。

修复建议：

- 每个 spine HTML item 只递增一次。
- 全流程统一使用 `currentIdx` 作为该章节 index。
- placeholder 也必须 `index = currentIdx`、`fallback = defaultTitle(currentIdx)`。
- 空章节如果要占位，也应以 `currentIdx` 生成空 ChapterContent，不要直接 continue 导致列表缺项。

### P0-2：后台章节加载无法正确合并

位置：

- `app/src/main/java/com/engreader/app/ui/main/MainScreenViewModel.kt:132`
- `app/src/main/java/com/engreader/app/ui/main/MainScreenViewModel.kt:158`
- `app/src/main/java/com/engreader/app/ui/main/MainScreenViewModel.kt:169`
- `app/src/main/java/com/engreader/app/ui/main/MainScreenViewModel.kt:179`

现状：

- `parseBook(..., 0, initialCount)` 返回的 `parsed.chapters.size` 被当作 totalChapters。
- 如果 `parseBook()` 只返回前 5 章，`totalChapters` 就是 5。
- 后续 `if (totalChapters > initialCount)` 很可能永远不成立。
- 即使成立，合并逻辑 `if (idx in mergedChapters.indices)` 只能替换已有位置，不能 append 超出当前列表的新章节。

风险：

- 大书只能打开前几章。
- 目录和章节总数不完整。
- 预渲染进度与真实可读章节不一致。

修复建议：

- `ParsedBook` 应额外携带 `totalSpineChapters`。
- 初次打开时 readerState 可以先放 placeholder chapter list，长度等于 totalSpineChapters。
- 后台加载返回 chapter 后，按 `chapter.index` 替换 placeholder。
- 或者暂时取消 range 加载，先一次性返回完整章节，等稳定后再做懒加载。

### P0-3：GBK/GB18030 声明被错误按 UTF-8 解码

位置：

- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:645`
- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:652`

现状：

```kotlin
"gb2312", "gbk", "gb18030" -> Charsets.UTF_8 // Use UTF-8 as superset; GBK needs ICU
```

风险：

- UTF-8 不是 GBK/GB18030 的超集。
- 声明为 GBK/GB18030 的中文 EPUB 会继续乱码。
- 这正好对应用户之前反馈的书名/目录乱码问题。

修复建议：

- Android/Java 可使用 `Charset.forName("GB18030")` 或 `Charset.forName("GBK")`。
- 声明为 `gb2312` 建议也使用 GB18030 解码。
- 如果声明编码不可用，再 fallback UTF-8。

### P0-4：OPF metadata fallback 基本无效

位置：

- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:463`
- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt:465`

现状：

`readMetadataSafe()` 尝试在 `book.spine.spineReferences` 中找 href 以 `.opf` 结尾的 resource。

风险：

- OPF 文件不是 spine 章节，通常不会出现在 spineReferences 中。
- fallback 大概率永远走不到。
- 书名/作者乱码时仍只能依赖 epub4j metadata。

修复建议：

- 使用 `EpubStructureInspector.readContainer(file)` 拿到 OPF path，再从 ZipFile 读取 OPF bytes。
- 或直接在 `EpubStructureInspector` 中增加 `readOpfMetadata(file)`。
- metadata 解析必须基于 OPF 实际 bytes，而不是 spine resource。

## 3. 分层 Review

### 3.1 ZIP 解压/校验

当前改善：

- 已新增 `EpubStructureInspector.validate(file)`。
- 已检查 `mimetype`、`META-INF/container.xml`、container 指向的 OPF 是否存在。
- `EpubImportService` 已改为复制后用 inspector 校验。
- 文件大小检查改用 `OpenableColumns.SIZE`，比 `available()` 更合理。

剩余风险：

- 仍保留 ZIP header 预检查，但只看 `PK` 前两字节，实际结构校验依赖后续 ZipFile，问题不大。
- `mimetype` 按规范必须是 ZIP 第一项且不压缩；当前只检查内容，不检查位置和压缩方式。
- 如果 `OpenableColumns.SIZE` 返回 0 或未知，大文件限制不会生效。

修复建议：

- 可选增强：检查 `mimetype` 是否为第一个 entry 且 `method == STORED`。
- 复制完成后用 `destFile.length()` 再做一次大小限制。

优先级：P1。

### 3.2 container.xml 定位 OPF

当前改善：

- 新增 `EpubStructureInspector.checkContainer()`。
- 可以读取 container.xml 并提取 rootfile full-path。
- 可以计算 `opfBaseDir`。

剩余风险：

- XML 解析使用正则，不是 XML parser。
- `rootfileRegex` 要求 `full-path` 出现在 `<rootfile ...>` 中且标签以 `>` 结束，容错有限。
- `oebpsRegex` 被定义但没有使用。
- media-type 选择逻辑有 bug：循环中 `val line = rootfileRegex.find(xml)?.value` 每次都拿第一个 rootfile，而不是当前 rf 所在 rootfile。
- 如果 rootfile 属性顺序是 `full-path` 在前、`media-type` 在后，优先逻辑可能失效。

修复建议：

- 使用 Android XML pull parser 或标准 XML parser 解析 container.xml。
- 遍历所有 rootfile 节点，按节点读取 full-path 和 media-type。
- 优先 `media-type="application/oebps-package+xml"` 的 rootfile。
- 将 `opfPath` 做 URL decode 和路径规范化。

优先级：P0/P1。

### 3.3 OPF manifest 和 spine 解析

当前改善：

- HTML 判断已优先看 media-type：`application/xhtml+xml` / `text/html`。
- 图片判断也看 mediaType contains image。

剩余风险：

- 项目仍没有显式解析 OPF manifest/spine。
- `EpubStructureInspector` 只检查 OPF 文件存在，不解析 manifest/spine 内容。
- 图片资源仍通过 epub4j + 反射读取 resources。
- `linear="no"`、nav document、NCX、guide 未显式处理。
- 没有保存 manifest item 的 id/href/media-type/properties，因此后续资源路径解析仍缺少完整上下文。

修复建议：

- 在 inspector 中新增 OPF parser：
  - manifest items
  - spine itemrefs
  - nav item
  - ncx item
  - cover item
- 章节加载以 OPF spine itemref 为唯一真源。
- 图片/CSS/link 解析使用 manifest map，而不是反射和 simpleName。

优先级：P0。

### 3.4 XHTML 章节是否按 spine 顺序加载

当前改善：

- `parseBook()`、`preRenderAll()`、`parseChapterRange()` 都按 `book.spine.spineReferences` 遍历。
- `parseChapterRange()` 目前是每个 HTML spine item 递增一次，逻辑相对正确。

剩余风险：

- `parseBook()` 存在 P0-1 的二次递增 bug。
- `parseBook()` 遇到 blank rawHtml 仍直接 continue，不生成占位章节。
- `preRenderAll()` 遇到 blank rawHtml 也 continue，不生成占位章节。
- 空章节会导致 reader 的 `chapters` 列表不连续。
- ViewModel 仍使用 `index in chapters.indices` 判断跳转合法性；如果 chapter.index 与列表下标不一致，跳转会失败。

修复建议：

- 章节 list 的下标和 `ChapterContent.index` 必须保持一致。
- 对失败或空内容章节生成占位 ChapterContent。
- `ReaderUiState.currentChapterIndex` 应代表 spineIndex，不能混用列表 index。
- 更稳的做法是 `chaptersByIndex: Map<Int, ChapterContent>` 或固定长度 list。

优先级：P0。

### 3.5 文本编码解码

当前改善：

- 已新增 `readResourceText()`，优先读取 `resource.data`。
- 已支持 UTF-8 BOM、UTF-16 BOM、XML declaration、meta charset。
- 失败时会 Log warning，不再完全无声。

剩余风险：

- GB2312/GBK/GB18030 被错误映射到 UTF-8。
- `String(data, Charsets.UTF_8)` 对非法 UTF-8 不一定抛异常，可能用替换字符吞掉乱码，因此 `runCatching` 不能可靠判断 UTF-8 是否失败。
- meta charset regex 可能误匹配正文中的 charset 字符串，不限定 `<meta>` 或 header 范围结构。
- metadata 的 OPF fallback 无效。

修复建议：

- 使用 `CharsetDecoder` + `CodingErrorAction.REPORT` 检测 UTF-8 是否真的有效。
- 声明 GBK/GB18030 时使用 `Charset.forName("GB18030")`。
- 解析 meta charset 时限定 `<meta ... charset=...>` 或 content-type meta。
- 将 OPF metadata 解码纳入同一套 bytes decoder。

优先级：P0。

### 3.6 图片和 CSS 相对路径解析

当前改善：

- 图片 marker 已保留。
- SVG 有 placeholder，避免 BitmapFactory 直接失败。
- `extractCssImages()` 被接入 `parseChapterContent()`。

剩余风险：

- 图片路径仍是 simpleName、`../Images`、`endsWith(simpleName)` 等启发式匹配。
- 没有根据当前 XHTML 所在目录解析相对路径。
- 没有使用 OPF base dir。
- 同名图片会撞。
- CSS 只扫描 inline `<style>` 的 `background-image`，不处理外链 CSS。
- CSS 扫描只是往 imageMap 加映射，没有把背景图变成可渲染 block，因此大多数 CSS 背景图仍不会显示。
- `<image href="">`、`xlink:href`、`srcset`、SVG 内图片都未处理。

修复建议：

- 新增统一函数：`resolveEpubHref(opfBaseDir, currentResourceHref, relativeHref)`。
- imageMap key 使用 EPUB 内规范化绝对路径。
- 解析 OPF manifest 后用 manifest href 建资源表。
- 解析 XHTML 时把 `<img src>` 按当前 XHTML 路径解析，而不是 simpleName。
- 外链 CSS 至少应读取并解析基础样式和 background-image。

优先级：P1。

### 3.7 渲染层：段落、标题、图片、链接

当前改善：

- 渲染层能显示文本、部分标题、部分 blockquote、部分图片。
- SVG/图片失败时有文本 placeholder。
- 夜间模式下 ForegroundColorSpan 近黑色会跳过，降低黑字黑底风险。

剩余风险：

- 阅读页仍始终使用 `LazyColumn`，没有真正的分页渲染分支。
- `ReaderMode.PAGED` 只存在 ViewModel 设置，没有 UI 使用。
- 图片仍在 Composable 中同步 `BitmapFactory.decodeFile()`，大图会卡顿或 OOM。
- `Image` 只 `fillMaxWidth()`，没有按原图比例/最大高度约束。
- 内部链接 `chapter.xhtml#id`、`#note1` 仍直接 `ACTION_VIEW`，不会在 app 内跳转。
- 解析模型仍是 `paragraphs: List<String>` + `segments`，不是完整 block model。
- 标题层级依靠 `HtmlCompat` span 推断，CSS class 标题不会稳定识别。
- 英文 EPUB 的两端对齐还不符合用户要求：当前 `TextAlign.Justify` 可能通过拉大单词间距实现对齐；用户要求单词间距保持一致，右边缘放不下的单词应使用 `-` 连字符断词，并将剩余字符换到下一行。

修复建议：

- 分页模式单独实现，基于可见高度/字号/行高/边距测量分页。
- 图片使用异步加载，至少在 `remember(segment.imagePath)` 中缓存 bitmap，并限制 decode 尺寸。
- 内部链接转成 app 内跳转事件。
- 后续逐步升级 block model：Heading、Paragraph、Image、List、BlockQuote、Table、Link。
- 为英文正文新增“稳定单词间距 + 连字符断词”的排版策略：
  - 当用户选择两端对齐时，不应靠过度拉伸 word spacing 填满行宽。
  - 对超出行宽的英文单词，按合法断词位置插入 `-`，前半部分留在当前行，后半部分进入下一行。
  - 需要优先评估 Compose `Hyphens.Auto` 是否能满足真实设备表现；如果不能，应在分页/排版层自定义英文断词与行构造。
  - 断词策略应只作用于英文长单词，不应破坏中文、标点、URL、专有名词和已含连字符的单词。

优先级：P0/P1。

## 4. 对 7 个审查问题的逐项回答

### 1. ZIP 解压是否正确

部分正确。

导入已经从弱校验进步到结构校验，但还未检查 mimetype ZIP entry 的顺序/压缩方式。作为普通 EPUB 接受层已经明显改善。

### 2. container.xml 是否正确定位 OPF

不完全正确。

能读取 full-path 并检查 OPF 存在，但 XML 正则解析和 media-type 选择逻辑仍不稳。多 rootfile 或属性顺序变化可能出错。

### 3. OPF manifest 和 spine 是否正确解析

不正确/不完整。

OPF manifest 没有显式解析，spine 依赖 epub4j；资源表、nav、ncx、linear、cover properties 均未形成可靠内部模型。

### 4. XHTML 章节是否按 spine 顺序加载

存在严重 bug。

遍历来自 spine，但 `parseBook()` 章节 index 二次递增，placeholder 使用错误 index。必须优先修复。

### 5. 文本是否按 UTF-8/声明编码正确解码

部分正确。

已有 BOM/XML/meta 检测，但 GBK/GB18030 错误映射到 UTF-8，UTF-8 错误检测也不可靠。中文 EPUB 乱码风险仍高。

### 6. 图片和 CSS 相对路径是否正确解析

仍不正确。

图片显示能力有改善，但路径解析仍是启发式，不是基于 OPF base 和当前 XHTML href。CSS 支持非常有限。

### 7. 渲染层是否正确处理段落、标题、图片和链接

部分正确。

段落、部分标题、部分图片可显示；但分页未实现，图片同步解码，内部链接不会跳转，block model 不完整。英文两端对齐也还不满足新增要求：应保持单词间距一致，并在右边缘放不下英文单词时使用 `-` 断词换行。

## 5. 建议下一轮修复顺序

1. 修复 `parseBook()` 章节 index 二次递增和 placeholder index 错误。
2. 修复 `openBook()` 的 totalChapters/后台章节合并逻辑。
3. 修复 GBK/GB18030 解码，使用真实 Charset。
4. 让 metadata fallback 从 ZipFile 的 OPF path 读取 OPF bytes。
5. 用 XML parser 重写 container.xml rootfile 解析。
6. 显式解析 OPF manifest/spine/nav/ncx。
7. 建立 OPF base + 当前 XHTML href 的资源路径解析。
8. 实现真正的 Paged 渲染分支。
9. 图片异步/缩放加载，内部链接转 app 内跳转。
10. 实现英文两端对齐的稳定字距和连字符断词策略。

## 6. 建议新增测试

至少增加以下测试，否则这些问题很容易反复出现：

1. `EpubStructureInspector` 测试：标准 mimetype/container/OPF。
2. 多 rootfile container 测试：确保选中 `application/oebps-package+xml`。
3. OPF base path 测试：`OEBPS/content.opf` 下资源路径解析。
4. spine index 测试：空章节、非 HTML 资源、range parse 后 index 连续稳定。
5. GB18030 EPUB 测试：标题、目录、正文不乱码。
6. 同名图片测试：`Images/a.jpg` 与 `Figures/a.jpg` 不撞图。
7. 内部链接测试：`chapter2.xhtml#section` 跳转到 app 内章节。
8. Paged 模式测试：一页不应只显示一个段落，应按屏幕可见高度分页。
9. 英文两端对齐测试：单词间距不应被明显拉伸，长单词在右边缘应使用 `-` 断词换行。

## 7. 给下一个智能体的简短指令

先不要继续做视觉 UI。先修数据链路：

- 修 `parseBook()` index 二次递增。
- 修 totalChapters 和后台合并。
- 修 GBK/GB18030 解码。
- OPF metadata 从 ZipFile 真实 OPF path 读取。
- container.xml 改 XML parser。

完成后再进入资源路径、图片/CSS、分页和链接。

新增排版要求：英文 EPUB 在选择两端对齐阅读样式时，正文单词间距必须保持一致；屏幕右边缘显示不下的英文单词，需要用 `-` 连接前后并把剩余字符换到下一行。该要求应纳入分页/文本测量/行构造一起实现和测试。

## 8. 详细修改方案（给 Claude 执行）

本节是可执行修改路线。请按顺序实现，前一层稳定后再进入下一层。

### 8.1 第一阶段：修复章节 index 与懒加载合并

目标：

- 保证 `ChapterContent.index`、章节列表下标、TOC 跳转、书签、阅读进度使用同一套稳定 spine index。
- 避免 `parseBook()` 只加载前 5 章后导致后续章节永远不加载。

修改文件：

- `app/src/main/java/com/engreader/app/storage/EpubTextExtractor.kt`
- `app/src/main/java/com/engreader/app/model/Models.kt`
- `app/src/main/java/com/engreader/app/ui/main/MainScreenViewModel.kt`

具体修改 1：扩展 `ParsedBook`

位置：

- `Models.kt` 的 `data class ParsedBook`

建议结构：

```kotlin
data class ParsedBook(
  val bookTitle: String? = null,
  val author: String? = null,
  val chapters: List<ChapterContent>,
  val totalChapters: Int = chapters.size,
)
```

注意：

- 如果担心影响调用点，可给 `totalChapters` 默认值。
- `totalChapters` 表示 EPUB spine 中 HTML 章节总数，不是当前已解析章节数量。

具体修改 2：修复 `parseBook()` 双重递增

位置：

- `EpubTextExtractor.kt`
- `parseBook()`

当前风险代码：

```kotlin
val currentIdx = chapterIndex
chapterIndex++
...
fallback = defaultTitle(chapterIndex)
...
index = chapterIndex
...
chapterIndex += 1
```

应改为：

```kotlin
val currentIdx = chapterIndex
chapterIndex += 1
```

并且该 spine item 的所有地方都用 `currentIdx`：

```kotlin
fallback = defaultTitle(currentIdx)
index = currentIdx
```

空章节处理建议：

```kotlin
if (rawHtml.isBlank()) {
  allChapters += ChapterContent(
    index = currentIdx,
    title = defaultTitle(currentIdx),
    paragraphs = emptyList(),
    styledParagraphs = emptyList(),
    segments = emptyList(),
  )
  continue
}
```

具体修改 3：`preRenderAll()` 与 `parseChapterRange()` 统一策略

位置：

- `EpubTextExtractor.kt`
- `preRenderAll()`
- `parseChapterRange()`

要求：

- 每个 HTML spine item 只递增一次。
- 空章节也生成占位，除非明确决定全链路都跳过空章节。
- `totalChapters` 用 HTML spine item 总数。
- `ParsedBook(totalChapters = totalChapters)` 必须返回真实总数。

具体修改 4：修复 ViewModel 合并逻辑

位置：

- `MainScreenViewModel.kt`
- `openBook()`

当前风险：

```kotlin
readerState to parsed.chapters.size
```

应改为：

```kotlin
readerState to parsed.totalChapters
```

后台合并当前只能替换已有下标：

```kotlin
if (idx in mergedChapters.indices) {
  mergedChapters[idx] = chapter
}
```

建议改成固定长度 placeholder 列表。打开书时：

```kotlin
val chapterSlots = MutableList(parsed.totalChapters) { index ->
  parsed.chapters.firstOrNull { it.index == index }
    ?: ChapterContent(index = index, title = "Chapter ${index + 1}", paragraphs = emptyList())
}
```

后台合并：

```kotlin
if (idx in mergedChapters.indices) {
  mergedChapters[idx] = chapter
}
```

这样 `idx` 一定能落进固定长度列表。

验收点：

- 打开一本 10 章以上 EPUB，目录应显示全部章节或占位章节。
- 前 5 章之外的章节加载后能替换占位。
- 书签、目录跳转、搜索跳转不应越界。

### 8.2 第二阶段：修复 container.xml 解析

目标：

- 用真正 XML parser 替换正则解析。
- 正确选择 OPF rootfile。

修改文件：

- `app/src/main/java/com/engreader/app/storage/EpubStructureInspector.kt`

具体修改：

- 保留 `ContainerInfo` 和 `ValidationResult` 数据结构。
- 替换 `checkContainer()` 内部实现。
- 使用 Android 可用的 XML Pull Parser。

建议实现结构：

```kotlin
private fun checkContainer(zip: ZipFile, errors: MutableList<String>): ContainerInfo? {
  val entry = zip.getEntry("META-INF/container.xml") ?: ...
  val xml = zip.getInputStream(entry).use { it.readBytes() }
  val rootfiles = parseRootfiles(xml)
  val best = rootfiles.firstOrNull { it.mediaType == "application/oebps-package+xml" }
    ?: rootfiles.firstOrNull()
    ?: return null
  ...
}
```

建议新增私有结构：

```kotlin
private data class Rootfile(
  val fullPath: String,
  val mediaType: String,
)
```

`parseRootfiles()` 要读取：

- 标签名：`rootfile`
- 属性：`full-path`
- 属性：`media-type`

路径规范化：

- 去掉开头 `/`
- URL decode
- 保留大小写用于 ZipFile `getEntry()`，另可建立 lower-case 匹配 fallback

验收点：

- `container.xml` 中属性顺序变化不影响解析。
- 多 rootfile 时优先 `application/oebps-package+xml`。
- container 中 OPF path 为 `OEBPS/content.opf` 时能正确得到 `opfBaseDir = OEBPS`。

### 8.3 第三阶段：OPF metadata、manifest、spine 显式解析

目标：

- metadata fallback 从真实 OPF bytes 读取。
- manifest/spine/nav/ncx 形成可诊断数据，后续资源路径和目录修复有依据。

修改文件：

- `EpubStructureInspector.kt`
- `EpubTextExtractor.kt`

建议新增数据结构：

```kotlin
data class OpfInfo(
  val opfPath: String,
  val opfBaseDir: String,
  val title: String?,
  val creator: String?,
  val manifest: List<ManifestItem>,
  val spine: List<SpineItemRef>,
)

data class ManifestItem(
  val id: String,
  val href: String,
  val mediaType: String,
  val properties: String?,
)

data class SpineItemRef(
  val idref: String,
  val linear: Boolean,
)
```

建议新增方法：

```kotlin
fun readOpfInfo(file: File): OpfInfo?
```

实现路径：

1. `readContainer(file)` 拿到 `opfPath`。
2. `ZipFile(file).getEntry(opfPath)` 读取 OPF bytes。
3. 使用 `EpubTextExtractor` 现有解码逻辑，或将 `decodeBytes()` 移成可复用工具。
4. 用 XML parser 解析：
   - `metadata/dc:title`
   - `metadata/dc:creator`
   - `manifest/item`
   - `spine/itemref`

对 `readMetadataSafe()` 的修改：

- 不要从 `book.spine.spineReferences` 找 `.opf`。
- `EpubTextExtractor.extractMetadata(uri)` 可以先把 `Uri.fromFile(...)` 转成 File，然后调用 inspector 的 `readOpfInfo(file)`。
- 如果 OPF metadata 解码成功，优先使用 OPF title/creator。
- epub4j metadata 作为 fallback。

验收点：

- 书名不应再显示随机 ID。
- 中文 OPF metadata 正常显示。
- OPF path 不在根目录时也能读取 metadata。

### 8.4 第四阶段：修复 UTF-8/声明编码/GB18030 解码

目标：

- 按 BOM/XML/meta charset 正确解码 XHTML 和 OPF。
- GB2312/GBK/GB18030 中文 EPUB 不乱码。

修改文件：

- `EpubTextExtractor.kt`
- 如抽出工具，也可新增 `EpubTextDecoder.kt`

当前必须改掉：

```kotlin
"gb2312", "gbk", "gb18030" -> Charsets.UTF_8
```

应改为：

```kotlin
"gb2312", "gbk", "gb18030" -> Charset.forName("GB18030")
```

建议新增安全 UTF-8 检测：

```kotlin
private fun decodeStrict(data: ByteArray, charset: Charset): String? {
  return runCatching {
    charset.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(data))
      .toString()
  }.getOrNull()
}
```

解码顺序：

1. BOM
2. XML declaration encoding
3. HTML meta charset
4. declared charset strict decode
5. UTF-8 strict decode
6. GB18030 fallback
7. ISO-8859-1 last fallback

验收点：

- UTF-8 EPUB 正常。
- GBK/GB18030 中文标题、目录、正文正常。
- 解码失败时日志包含 href 和 fallback 编码。

### 8.5 第五阶段：修复图片/CSS 相对路径解析

目标：

- 不再靠 simpleName/endsWith 猜图。
- 使用 OPF base + 当前 XHTML href 解析资源路径。

修改文件：

- `EpubTextExtractor.kt`
- 必要时新增 `EpubResourceResolver.kt`

建议新增函数：

```kotlin
private fun resolveEpubHref(
  opfBaseDir: String,
  currentResourceHref: String,
  relativeHref: String,
): String
```

规则：

- 如果 `relativeHref` 是外部 URL，直接标记为 external，不走图片本地解析。
- 去掉 fragment：`substringBefore('#')`。
- URL decode。
- 如果是绝对 EPUB 路径，去掉开头 `/`。
- 如果是相对路径，以 `currentResourceHref` 所在目录为 base。
- 归一化 `.` 和 `..`。
- 最终 key 使用 EPUB 内规范化路径，例如 `OEBPS/Images/a.jpg`。

`extractImages()` 修改方向：

- imageMap key 改为规范化 EPUB 内路径。
- 尽量用 OPF manifest items 建资源表。
- 反射读取 epub4j resources 可暂时保留为 fallback，但不要作为主路径。

`replaceImgTags()` 修改方向：

- 参数增加 `resourceHref` 和 `opfBaseDir`。
- 对 `<img src>` 调用 `resolveEpubHref()`。
- 用 resolved key 查 imageMap。

验收点：

- `Text/chapter1.xhtml` 引用 `../Images/pic.jpg` 能显示。
- 同名不同目录图片不会撞。
- URL encoded 路径能显示。

### 8.6 第六阶段：渲染层分页、图片、链接

目标：

- 真实实现滚动/分页两种模式。
- 图片不卡 UI。
- 内部链接能在 app 内跳转。

修改文件：

- `MainScreen.kt`
- `MainScreenViewModel.kt`
- `Models.kt`

分页实现路径：

1. `ReaderContent()` 中根据 `settings.readerMode` 分支：

```kotlin
when (settings.readerMode) {
  ReaderMode.VERTICAL -> VerticalReaderContent(...)
  ReaderMode.PAGED -> PagedReaderContent(...)
}
```

2. `VerticalReaderContent` 保留当前 `LazyColumn`。
3. `PagedReaderContent` 不应一页一个段落，应按可见区域测量文本高度。
4. 初版可以先用章节内段落累积高度分页，后续再精细到单词/行。
5. 页面切换保存 `currentChapterIndex` 和页内 paragraph offset。

图片修改路径：

- `BitmapFactory.decodeFile()` 不要直接在 Composable 每次重组执行。
- 至少改成：

```kotlin
val bitmap = remember(segment.imagePath) {
  decodeScaledBitmap(segment.imagePath, maxWidthPx, maxHeightPx)
}
```

- 更好的做法是引入或使用异步图片加载，但如果不新增依赖，先做 decode 尺寸限制。

链接修改路径：

- URLSpan 的 annotation 要区分：
  - external URL：`http://`、`https://`
  - internal href：`#id`、`chapter.xhtml#id`
- 内部链接触发 ViewModel 跳转，不要 `ACTION_VIEW`。

验收点：

- Paged 模式一页显示接近一屏内容，不是一段。
- 大图片不会明显卡顿。
- 点击内部目录/脚注链接能留在 app 内跳转。

### 8.7 第七阶段：英文两端对齐与连字符断词

目标：

- 英文 EPUB 在用户选择“两端对齐”时，单词间距保持一致。
- 一行右边缘显示不下的英文单词，用 `-` 连字符断开，前半部分留当前行，剩余字符换到下一行。

修改文件：

- `MainScreen.kt`
- 可能新增：`EnglishHyphenator.kt` 或 `TextLineBreaker.kt`
- 如果分页层独立实现，也应放在分页/文本测量模块中。

当前相关代码：

- `ParagraphWithGestures()`
- `TextStyle(textAlign = textAlign, hyphens = if (textAlign == TextAlign.Justify) Hyphens.Auto else Hyphens.Unspecified)`

风险：

- Compose 的 `TextAlign.Justify` 可能通过拉大单词间距实现两端对齐。
- `Hyphens.Auto` 是否生效依赖平台、语言、Text 组件能力和 hyphenation 配置，不能只设置后就认为完成。

推荐实现路径：

1. 先实测 `Hyphens.Auto + TextAlign.Justify` 在目标手机是否满足：
   - 单词间距是否被明显拉伸
   - 长单词是否出现 `-`
2. 如果不满足，新增自定义英文行构造：
   - 输入：段落字符串、可用宽度、TextStyle、TextMeasurer
   - 输出：带换行和 `-` 的字符串，或者按行渲染的 list
3. 行构造逻辑：
   - 按 word token 拆分英文文本，保留标点。
   - 逐词测量当前行加下一个词是否超宽。
   - 如果普通词超宽，尝试在合法断点插入 `-`。
   - 当前行放 `prefix-`，下一行继续 `suffix`。
   - 普通空格宽度固定，不通过拉伸空格实现对齐。
4. 断词规则初版：
   - 只处理 `[A-Za-z]{6,}` 的长英文单词。
   - 不处理 URL、邮箱、数字串、含 `/` 的路径。
   - 已含 `-` 的单词优先在原有连字符后断。
   - 不在前 3 个字符或最后 3 个字符断开。
5. 渲染策略：
   - 两端对齐开启时，英文段落使用自定义 line breaker 的结果。
   - 中文段落、混合复杂段落先 fallback 到普通 BasicText。

建议新增函数：

```kotlin
private fun layoutEnglishJustifiedWithHyphenation(
  text: String,
  maxWidthPx: Float,
  textStyle: TextStyle,
  textMeasurer: TextMeasurer,
): String
```

验收点：

- 英文段落中普通单词间距肉眼一致。
- 长单词在右边缘无法完整显示时出现 `-`。
- 断词后下一行继续剩余字符。
- URL 不被拆坏。
- 中文正文不受影响。

### 8.8 推荐执行与验证命令

每完成一个阶段运行：

```powershell
.\gradlew.bat assembleDebug
```

如涉及单元测试，运行：

```powershell
.\gradlew.bat testDebugUnitTest
```

最终构建：

```powershell
.\gradlew.bat clean assembleDebug
```

APK 路径：

```text
app\build\outputs\apk\debug\app-debug.apk
```
