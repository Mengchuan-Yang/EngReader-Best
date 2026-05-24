# EngReader 开发项目代码审查报告 (Code Review Report)

本报告针对当前正在进行的 Android EPUB 英文阅读器项目（`epub-reader-app`）进行了全面的代码审查。在本次审查中，我们**仅对代码进行分析和审查，未对项目中的任何源文件进行修改**。

审查的重点在于识别代码中的潜在缺陷、性能瓶颈、架构设计问题以及可能导致运行期崩溃（OOM、ANR 等）的安全隐患。以下为审查出的具体问题和详细报告。

---

## 目录
1. [严重缺陷与运行期 Bug (Critical Bugs & Runtime Issues)](#1-严重缺陷与运行期-bug-critical-bugs--runtime-issues)
   - 1.1 [单词翻译渲染与点击映射的严重逻辑缺陷 (Offset Mapping Bug)](#11-单词翻译渲染与点击映射的严重逻辑缺陷-offset-mapping-bug)
   - 1.2 [极端的 I/O 写入性能瓶颈 (Recomposition & I/O Loop)](#12-极端的-io-写入性能瓶颈-recomposition--io-loop)
   - 1.3 [备份还原 (Restore) 阶段的高风险 OOM 隐患](#13-备份还原-restore-阶段的高风险-oom-隐患)
   - 1.4 [备份还原中的非事务破坏性操作](#14-备份还原中的非事务破坏性操作)
   - 1.5 [解析大尺寸 EPUB 文件时的 OOM 隐患](#15-解析大尺寸-epub-文件时的-oom-隐患)
   - 1.6 [查词查句重复发送网络请求的费用与带宽浪费 (Redundant API Request Leak)](#16-查词查句重复发送网络请求的费用与带宽浪费-redundant-api-request-leak)
   - 1.7 [密钥库主线程同步初始化引起的界面卡顿 (KeyStore Main-Thread Initialization Jank)](#17-密钥库主线程同步初始化引起的界面卡顿-keystore-main-thread-initialization-jank)
2. [架构与设计问题 (Architectural & Design Flaws)](#2-架构与设计问题-architectural--design-flaws)
   - 2.1 [两套并行的本地存储方案 (Room 数据库彻底闲置)](#21-两套并行的本地存储方案-room-数据库彻底闲置)
   - 2.2 [Room 缺少 Annotation Processor / KSP 依赖](#22-room-缺少-annotation-processor--ksp-依赖)
   - 2.3 [外部公共存储导入书籍的不安全性与脆弱性](#23-外部公共存储导入书籍的不安全性与脆弱性)
   - 2.4 [包名与目录结构不匹配 (Mismatched Package & Directory)](#24-包名与目录结构不匹配-mismatched-package--directory)
   - 2.5 [冗余的死代码与占位符客户端 (Unused Placeholder Clients)](#25-冗余的死代码与占位符客户端-unused-placeholder-clients)
3. [开发与测试问题 (Development & Testing Issues)](#3-开发与测试问题-development--testing-issues)
   - 3.1 [单元测试文件编译损坏 (Broken Unit Tests)](#31-单元测试文件编译损坏-broken-unit-tests)
   - 3.2 [用户界面功能阻断 (Inaccessible Bookmarks & Search)](#32-用户界面功能阻断-inaccessible-bookmarks--search)
4. [代码细节与规范改进 (Details & Recommendations)](#4-代码细节与规范改进-details--recommendations)
   - 4.1 [网络请求资源泄漏 (Connection Leak)](#41-网络请求资源泄漏-connection-leak)
   - 4.2 [排版与阅读样式丢失 (Loss of Text Styling)](#42-排版与阅读样式丢失-loss-of-text-styling)
   - 4.3 [DeepSeek API 端点非官方标准路径 (DeepSeek API Endpoint Non-Standard Path)](#43-deepseek-api-端点非官方标准路径-deepseek-api-endpoint-non-standard-path)
   - 4.4 [频繁创建无状态服务实例的开销 (Short-Lived Service Instantiation)](#44-频繁创建无状态服务实例的开销-short-lived-service-instantiation)
5. [EPUB 文件渲染性能与多媒体排版精度分析 (EPUB Rendering Performance & Media Accuracy Analysis)](#5-epub-文件渲染性能与多媒体排版精度分析-epub-rendering-performance--media-accuracy-analysis)
   - 5.1 [打开大文件卡顿与首加载时延 (Upfront Full-Book Parsing Lag)](#51-打开大文件卡顿与首加载时延-upfront-full-book-parsing-lag)
   - 5.2 [排版与 CSS 样式完全丢失 (Complete Loss of Styling & CSS)](#52-排版与-css-样式完全丢失-complete-loss-of-styling--css)
   - 5.3 [图片与多媒体文件完全丢失 (Total Loss of Images & Multimedia)](#53-图片与多媒体文件完全丢失-total-loss-of-images--multimedia)
   - 5.4 [翻页模式（Paged Mode）下的字符截断与主线程卡顿](#54-翻页模式paged-mode下的字符截断与主线程卡顿)
   - 5.5 [缺乏本地 Web 容器托管（Web-based Serving Missing）](#55-缺乏本地-web-容器托管web-based-serving-missing)

---

## 1. 严重缺陷与运行期 Bug (Critical Bugs & Runtime Issues)

### 1.1 单词翻译渲染与点击映射的严重逻辑缺陷 (Offset Mapping Bug)
* **涉及文件**：
  * [MainScreen.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreen.kt) 中的 `renderParagraphWithAnnotations` (L856-L893) 与 `mapRenderedOffsetToOriginal` (L895-L937)
* **问题描述**：
  在段落内插入单词标注（如 `The quick fox（狐狸） jumps...`）并反向映射用户的点击偏移量（Char Offset）时，代码是直接对 `annotations` 列表进行 `forEach` 处理的。
  然而，`state.annotations` 在 [AppStateStore.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/AppStateStore.kt) 中是按**时间创建顺序**（即用户的查词顺序）直接追加生成的，并没有按照单词在段落中的**线性字符位置（字符偏移）**进行排序。
  这会导致严重的 Bug：
  1. 如果用户先翻译了位于段落末尾的单词（如 `"lazy"`），再翻译段落开头的单词（如 `"quick"`）。
  2. 渲染和映射函数会先处理 `"lazy"`，将 `searchStart` 推进到 `"lazy"` 之后。
  3. 下一次循环处理 `"quick"` 时，因为 `startIndex = searchStart` 已经超越了 `"quick"` 实际的位置，`rendered.indexOf("quick", startIndex = searchStart)` 将会返回 `-1`，导致 `"quick"` 的翻译**无法被渲染在屏幕上**。
  4. 同样，在 `mapRenderedOffsetToOriginal` 中也存在此问题，会导致点击位置偏移量计算完全错乱，用户点击已翻译单词可能毫无反应或查词范围错位。
* **修复建议**：
  在遍历 `wordAnnotations` 之前，必须在内存中先根据单词在段落中的首个出现位置（即 `paragraph.indexOf(it.anchorText)`）对列表进行升序排序：
  ```kotlin
  val wordAnnotations = annotations
      .filter { it.type == AnnotationType.WORD }
      .sortedBy { paragraph.indexOf(it.anchorText, ignoreCase = true) }
  ```

### 1.2 极端的 I/O 写入性能瓶颈 (Recomposition & I/O Loop)
* **涉及文件**：
  * [MainScreenViewModel.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreenViewModel.kt) 中的 `translateWordAt` (L207-L221)
  * [AppStateStore.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/AppStateStore.kt) 中的 `persist` (L181-L184)
* **问题描述**：
  当用户将查词查句的重复模式设置为 `RepeatAnnotationMode.BOOK_AUTO`（全书自动标注）或 `RepeatAnnotationMode.CHAPTER_AUTO`（整章自动标注）时，ViewModel 会通过循环找出所有包含该词的段落，并依次调用 `addWordAnnotationIfMissing`。
  然而，`addWordAnnotationIfMissing` 每次都会触发 `stateStore.addAnnotation()`，而 `stateStore` 的每次写入又会通过 `persist` 触发**整个 JSON 文件的同步序列化和磁盘写入（`stateFile.writeText`）**以及 Flow 值的更新。
  如果一本书中有 300 处出现了该词，代码会依次执行 **300 次同步文件写入 I/O** 并频繁推送 StateFlow 更新，这极易引起主线程或背景线程严重阻塞，烧写闪存，甚至引发 UI 极其卡顿乃至 ANR。
* **修复建议**：
  在 `AppStateStore` 中提供一个批量添加的接口，仅在全部遍历和添加完毕后进行**一次**序列化写入操作：
  ```kotlin
  suspend fun addAnnotations(items: List<AnnotationRecord>) {
      persist(_state.value.copy(annotations = _state.value.annotations + items))
  }
  ```

### 1.3 备份还原 (Restore) 阶段的高风险 OOM 隐患
* **涉及文件**：
  * [BackupZipService.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/BackupZipService.kt) 中的 `restoreFromZip` (L50-L75)
* **问题描述**：
  在从 ZIP 备份还原时，代码使用 `ZipInputStream` 遍历所有条目。对于每一个 EPUB 书籍条目，它都直接调用了 `zip.readBytes()` 并将其存放在 `payloadsByBookId: LinkedHashMap<String, ByteArray>` 集合中：
  ```kotlin
  payloadsByBookId[id] = zip.readBytes()
  ```
  如果用户备份了 15 本书籍，每本书平均大小为 15MB，则在还原时会在 JVM 堆内存（Heap）中同时驻留高达 **225MB** 的 `ByteArray` 对象。这在移动端（尤其是低内存的 Android 设备上）会瞬间耗尽 JVM Heap 导致 `OutOfMemoryError` 进程崩溃。
* **修复建议**：
  应当在遍历 ZIP 的过程中，直接将读到的输入流写入到文件系统（如缓存文件），仅在 `payloadsByBookId` 中存储它们解压后的**临时文件路径**，待状态合并完成后再统一重命名或移动，而不是将整个文件内容直接常驻于内存。

### 1.4 备份还原中的非事务破坏性操作
* **涉及文件**：
  * [BackupZipService.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/BackupZipService.kt) (L79-L91)
* **问题描述**：
  在 `restoreFromZip` 的后半段中，代码会首先清空本地所有的现有书籍：
  ```kotlin
  stateStore.snapshot().books.forEach { existingBook ->
      runCatching { resolver.delete(Uri.parse(existingBook.sourceUri), null, null) }
  }
  ```
  然后再尝试向公共存储（MediaStore）写入解压出的书籍数据。
  这是一种非常危险的“非事务性/非回滚式”操作。如果在删除旧书后，写入新书的过程中遭遇异常（比如空间不足、权限变更或 ZIP 损坏），用户原有的书籍已经彻底丢失，且未能成功还原备份，会导致数据严重丢失。
* **修复建议**：
  应该先解压并验证还原的数据是否全部写入成功，在确信无误之后，再去执行原图书的清理，或者提示用户是否覆盖并以覆盖的形式更新存储，确保操作的原子性。

### 1.5 解析大尺寸 EPUB 文件时的 OOM 隐患
* **涉及文件**：
  * [EpubTextExtractor.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/EpubTextExtractor.kt) 中的 `parseBook` (L85-L90) 与 `extractCover` (L25-L31)
* **问题描述**：
  目前在解析 EPUB 文件时，代码通过 `contentResolver.openInputStream(uri)` 获取流，并传入 `EpubReader().readEpub(stream)`。
  对于 epub4j 库来说，使用 `readEpub(InputStream)` 方式会将 EPUB 的所有资源文件（包括插图、字体等）完全读取并缓存在内存的 `ByteArray` 数组中。如果导入的文件含有大量精美插图或体积较大（比如 30MB+），就会极其容易发生 OOM 崩溃。
* **修复建议**：
  epub4j 推荐对大文件使用基于 `java.util.zip.ZipFile` 的构造器，这需要将流保存为本地的临时 File，随后调用 `EpubReader().readEpub(ZipFile)`，此方法在读取资源时只加载元数据，并根据需要按需（On-demand）流式读取，可大幅节省内存。

### 1.6 查词查句重复发送网络请求的费用与带宽浪费 (Redundant API Request Leak)
* **涉及文件**：
  * [MainScreenViewModel.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreenViewModel.kt) 中的 `translateWordAt` (L192-L204) 和 `translateSentenceAt` (L232-L242)
* **问题描述**：
  当用户点击已经翻译并标注过的单词或长按已被翻译过的句子时，程序仍会无条件构建请求并调用 FallbackTranslationService 进行 API 网络请求。
  对是否已存在标注的查重（`stateStore.hasWordAnnotation`）仅在**网络请求成功回调后**才会执行。这使得如果用户误触或反复点击同一单词，会无谓地向 OpenAI / Gemini / DeepSeek 接口发起重复网络请求，浪费大量的 API Key Token 余额与网络带宽。
* **修复建议**：
  在发起翻译的协程之前，优先进行本地状态检查。若当前单词/句子在当前章节及段落已存在有效翻译，直接复用本地的标注缓存，不再构建并触发网络请求：
  ```kotlin
  // 在 translateWordAt 中
  if (stateStore.hasWordAnnotation(reader.book.id, chapterIndex, paragraphIndex, word)) {
      // 可触发振动或弹窗提示，不再执行网络请求
      return 
  }
  ```

### 1.7 密钥库主线程同步初始化引起的界面卡顿 (KeyStore Main-Thread Initialization Jank)
* **涉及文件**：
  * [MainScreenViewModel.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreenViewModel.kt) 中的 `secureApiKeyStore` 初始化 (L43)
  * [SecureApiKeyStore.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/settings/SecureApiKeyStore.kt) 中的构造函数 (L9-L18)
* **问题描述**：
  在 `MainScreenViewModel` 的主构造函数中，`secureApiKeyStore` 是同步实例化的：
  ```kotlin
  private val secureApiKeyStore = SecureApiKeyStore(application.applicationContext)
  ```
  而在 `SecureApiKeyStore` 构造时，会使用 `MasterKey.Builder` 并在 Android KeyStore 系统中检索/生成 AES-256 密钥。
  由于 Android KeyStore 的硬件/底层机制可能产生较大延迟，此同步调用在主线程初始化时极易阻塞主线程达数首百毫秒（特别是在老旧低配设备首次启动时），引发冷启动明显的 UI 卡顿甚至被系统误判 ANR。
* **修复建议**：
  建议将 `prefs` 声明为 lazy 懒加载，在首次执行 `loadSettings`、`putApiKey` 等异步挂起方法时，在 `Dispatchers.IO` 背景线程中去触发初始化，避开 ViewModel 创建阶段的主线程开销。

---

## 2. 架构与设计问题 (Architectural & Design Flaws)

### 2.1 两套并行的本地存储方案 (Room 数据库彻底闲置)
* **涉及文件**：
  * [data](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/data) 包、[domain](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/domain) 包下的所有类文件
  * [AppStateStore.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/AppStateStore.kt)
* **问题描述**：
  项目中包含了非常完整、规范的 Room 数据库架构体系（包含 Entity, DAO, Repository 封装等）。但是，这些模块在核心业务（`MainScreenViewModel`）中完全没有被调用。
  实际上，应用程序完全依赖于 `AppStateStore.kt` 读写单个名为 `engreader_state.json` 的大 JSON 文本文件来管理图书列表、进度、书签和翻译标注。
  这导致了两个负面影响：
  1. **无用代码多**：Room DB 相关包成为了大体积的 Dead Code。
  2. **可伸缩性差**：JSON 全量序列化方案无法应对大规模的书签、大量的查词标注（几千条标注后读写效率会断崖式下降），后续还是必须迁移回 Room 数据库。
* **修复建议**：
  建议移除 `AppStateStore.kt`，将界面彻底重构并绑定到设计好的 Room 数据仓库层（`DataRepository` / `RoomBookRepository` 等），实现真正按需查询与持久化。

### 2.2 Room 缺少 Annotation Processor / KSP 依赖
* **涉及文件**：
  * [build.gradle.kts](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/build.gradle.kts)
* **问题描述**：
  在应用的依赖配置中，仅引入了 Room 库的 runtime 与 ktx：
  ```kotlin
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ```
  但是，项目中**没有添加 KSP 或 Kapt 编译器插件与 Room Compiler 的依赖项**。
  若以后尝试实例化 Room 数据库并编译，Kotlin 编译器由于没有 Room Compiler 注解处理器的生成类支持，在构建时会出现无法找到 `EngReaderDatabase_Impl` 类的编译错误。
* **修复建议**：
  在 `build.gradle.kts` 的 `plugins` 中应用 `com.google.devtools.ksp` 插件，并在 `dependencies` 中添加 `ksp(libs.androidx.room.compiler)`。

### 2.3 外部公共存储导入书籍的不安全性与脆弱性
* **涉及文件**：
  * [EpubImportService.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/EpubImportService.kt)
* **问题描述**：
  导入 EPUB 电子书时，该服务会使用 MediaStore 将书籍拷贝至共享的公共目录 `Documents/EngReader/books` 下。
  外部公共存储引入了以下风险：
  1. 用户可以在文件管理器中随意修改、重命名或删除此文件夹下的电子书，导致应用内记录的 `sourceUri` 失效变成死链。
  2. 卸载重装应用后，应用对 MediaStore 中已有的这些旧文件的所有权会丢失，必须重新请求复杂的存储读取权限。
* **修复建议**：
  阅读器类应用的标准做法是将导入的 EPUB 书籍存储在应用的内部私有目录（`context.filesDir` 或 `context.noBackupFilesDir`）下。这既能免除存储权限申请，又能在应用被卸载时自动清理，同时防止用户在外部误删。

### 2.4 包名与目录结构不匹配 (Mismatched Package & Directory)
* **涉及文件**：
  * `C:\Users\as705\Documents\EPUB READER APK\epub-reader-app\app\src\main\java\com\example\epubreader` 目录下的所有文件
* **问题描述**：
  所有 Kotlin 源文件声明的包名为：
  ```kotlin
  package com.engreader.app
  ```
  但是，它们被存放在 `com/example/epubreader/...` 的目录层级中。
  虽然 Android Gradle 允许在构建时解析不一致的目录结构，但在常规的 JVM / Android 团队协作中，这严重违反了包命名规约与代码目录一致性，导致 IDE（如 Android Studio）的代码新建、重构和自动导包功能时常发生冲突。
* **修复建议**：
  建议将包结构物理移动重构到符合实际包名的 `com/engreader/app` 目录下。

### 2.5 冗余的死代码与占位符客户端 (Unused Placeholder Clients)
* **涉及文件**：
  * [PlaceholderProviderClients.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ai/PlaceholderProviderClients.kt)
* **问题描述**：
  项目编写了离线/测试用的占位翻译客户端：`DeepSeekPlaceholderClient`、`GeminiPlaceholderClient` 和 `OpenAiPlaceholderClient`。
  然而，这三个客户端在整个程序中没有在任何地方被实例化或传入 `FallbackTranslationService`。这部分调试设计沦为完全被闲置的“死代码”。
* **修复建议**：
  若想支持测试模式，可在 API Settings 中增加一个“测试模式/Mock 翻译”开关，在 ViewModel 中根据此开关动态向 `FallbackTranslationService` 中注入 Mock 客户端。若不计划使用，应考虑清理该闲置文件。

---

## 3. 开发与测试问题 (Development & Testing Issues)

### 3.1 单元测试文件编译损坏 (Broken Unit Tests)
* **涉及文件**：
  * [MainScreenViewModelTest.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/test/java/com/example/epubreader/ui/main/MainScreenViewModelTest.kt) (L14-L21)
* **问题描述**：
  该项目包含的唯一测试文件 `MainScreenViewModelTest` 存在严重的语法与逻辑冲突，导致 `./gradlew test` 构建任务执行时会直接发生编译阻断：
  1. 试图通过传入 mock `DataRepository` 实例化 `MainScreenViewModel`，但 ViewModel 实际需要的构造参数是 Android `Application` 对象。
  2. 试图断言 `MainScreenUiState.Loading` 状态，但在实际代码中仅存在 `MainUiState` 这一数据结构，无此枚举或密封状态类。
* **修复建议**：
  必须使用符合当前 ViewModel 定义的构造逻辑，或者在 ViewModel 中引入依赖注入（如 Hilt），或支持传入 `SavedStateHandle` 和仓储接口以支持真正的单元测试。

### 3.2 用户界面功能阻断 (Inaccessible Bookmarks & Search)
* **涉及文件**：
  * [MainScreen.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreen.kt) (L691-L750)
* **问题描述**：
  在 `MainScreen.kt` 中设计并编写了功能完整的“书签列表对话框 (`showBookmarks`)”与“书籍内全文搜索对话框 (`showSearch`)”，并且在其对应的 ViewModel 中也实现好了对应的搜索和跳转逻辑。
  然而，在界面（Scaffold、TopAppBar、底栏等）中，**完全没有提供任何按钮、菜单项或手势事件去将 `showBookmarks` 或 `showSearch` 设置为 `true`**。
  这使得这两个重要的功能模块在 UI 层面处于彻底被隐藏和无法访问的状态（死代码 UI）。
* **修复建议**：
  在阅读器模式下的 TopAppBar 或底栏中，添加对应的书签列表按钮和搜索图标，绑定 `onClick = { showBookmarks = true }` 和 `onClick = { showSearch = true }`。

---

## 4. 代码细节与规范改进 (Details & Recommendations)

### 4.1 网络请求资源泄漏 (Connection Leak)
* **涉及文件**：
  * [NetworkProviderClients.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ai/NetworkProviderClients.kt) 中的 `postJson` (L203-L232)
* **问题描述**：
  在流式调用 AI API 的 `postJson` 函数中，使用 `HttpURLConnection` 发送 POST 并读取响应：
  ```kotlin
  val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply { ... }
  // ...
  return JSONObject(text)
  ```
  但是，该函数**没有任何地方执行过 `connection.disconnect()`**。
  虽然关闭输入输出流在一定程度上会释放资源，但是在 `HttpURLConnection` 中显式调用 `disconnect()` 是释放底层 Socket 连接和清理连接缓存的规范做法。在高频查词中，这可能导致底层长连接无法被适时回收甚至泄露。
* **修复建议**：
  将网络调用逻辑和流的处理放在 `try-finally` 中，并在 `finally` 块中确保调用 `connection.disconnect()`。

### 4.2 排版与阅读样式丢失 (Loss of Text Styling)
* **涉及文件**：
  * [EpubTextExtractor.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/EpubTextExtractor.kt) 中的 `sanitizeHtml` (L193-L201)
* **问题描述**：
  该函数在解析 EPUB 的 XHTML 内容时，简单粗暴地使用 `HtmlCompat.fromHtml(..., HtmlCompat.FROM_HTML_MODE_LEGACY).toString()` 过滤了所有的 HTML 标签，返回纯文本 String。
  这种做法剥离了文本中所有的 `<i>`, `<b>`, `<u>` 等样式标签以及 inline 图像占位符。这对于一款“阅读器”来说，会使展现出的图书丢失段落内原有的强调、斜体、引用等排版效果，造成非常扁平且不专业的阅读体验。
* **修复建议**：
  后续迭代时，不要直接转为 `toString()` 丢弃样式，应当保留一定的富文本格式（如 Spanned / AnnotatedString），或在 Compose 中使用类似 HTML-Render 的库，将基本的富文本渲染出来以确保阅读体验。

### 4.3 DeepSeek API 端点非官方标准路径 (DeepSeek API Endpoint Non-Standard Path)
* **涉及文件**：
  * [NetworkProviderClients.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ai/NetworkProviderClients.kt) (L93)
* **问题描述**：
  在 `DeepSeekProviderClient` 中，向以下端点发送请求：
  ```kotlin
  url = "https://api.deepseek.com/v1/chat/completions"
  ```
  根据 DeepSeek 官方 API 开放平台的标准文档，官方标准的 Chat Completions 服务路径是 `https://api.deepseek.com/chat/completions`（没有 `/v1` 子路径）。尽管目前一些网关可能做了重定向兼容，但使用非标路径会产生潜在的 API 访问 404 或解析延迟。
* **修复建议**：
  修改为官方标准地址 `https://api.deepseek.com/chat/completions`。

### 4.4 频繁创建无状态服务实例的开销 (Short-Lived Service Instantiation)
* **涉及文件**：
  * [MainScreenViewModel.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreenViewModel.kt) (L202, L241)
* **问题描述**：
  每次查词或查句操作，ViewModel 都在主线程中新建一个翻译服务实例：
  ```kotlin
  val service = FallbackTranslationService(secureApiKeyStore.loadSettings())
  ```
  这会随之重复调用 `defaultProviderClients()`，重新初始化并实例化三路 API 客户端类。这种频繁、短暂的服务与客户端创建不仅加剧了 JVM 垃圾回收（GC）的瞬时压力，且违反了单例或服务复用的面向对象设计规约。
* **修复建议**：
  应当使 `FallbackTranslationService` 成为生命周期内复用的实例，或在 ViewModel 内做缓存，仅在 API 密钥库发生修改或保存时，通知并重构其持有的 `settings` 结构。

---

## 5. EPUB 文件渲染性能与多媒体排版精度分析 (EPUB Rendering Performance & Media Accuracy Analysis)

根据您期望的“打开任何大小的 EPUB 文件都极其顺畅”以及“图片、多媒体文件和排版完全正确显示”的要求，当前项目的底层渲染和数据模型设计存在数个核心瓶颈：

### 5.1 打开大文件卡顿与首加载时延 (Upfront Full-Book Parsing Lag)
* **涉及代码**：
  * [MainScreenViewModel.kt:L97](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreenViewModel.kt#L97) (`openBook`)
  * [EpubTextExtractor.kt:L85](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/EpubTextExtractor.kt#L85) (`parseBook`)
* **问题描述**：
  目前打开图书时，代码会**一次性同步解析整本书的所有章节**，过滤所有的 HTML 标签，切分成段落，并构造成巨大的 `ParsedBook` 实体加载到内存中。
  对于一本字数极多或容量巨大的 EPUB 文件（如含有几百章的合集书或数十兆的图书），这个 upfront（前置）全量解析过程包含了大量的 Zip 读取和 `HtmlCompat.fromHtml` Native 调用，将耗时数秒到十几秒，导致 UI 长时间卡死在加载圈上，且堆内存消耗巨大。
* **修复建议**：
  应改用 **按需加载（On-demand Loading）**。在打开图书时仅解析并显示书籍的目录（TOC）结构和当前需要显示的章节（比如第一章），在用户滑动到下一章或翻页时，才异步解析并渲染对应的章节内容，从而实现任何大小书籍的秒开。

### 5.2 排版与 CSS 样式完全丢失 (Complete Loss of Styling & CSS)
* **涉及代码**：
  * [EpubTextExtractor.kt:L196](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/EpubTextExtractor.kt#L196) (`sanitizeHtml`)
* **问题描述**：
  函数直接调用 `HtmlCompat.fromHtml(...).toString()`，将原本解析出来的富文本 `Spanned` 直接强制转为纯文本 `String`，丢弃了全部的富文本 Span（如加粗 `<b>`、斜体 `<i>`、下划线 `<u>`、多级标题 `<h1-h6>`、文本居中对齐、段落边距等样式）。
  这会彻底使排版退化为类似 TXT 小说一样的纯字块显示。电子书内原本的代码块、诗歌排版、着重强调都将彻底失去样式。
* **修复建议**：
  如果在原生排版中，不应调用 `.toString()` 丢弃样式信息，而是将解析得到的 `Spanned` 或自定义的 `Html` 解析器结果封装为 Compose 的 `AnnotatedString` 并应用对应的 `SpanStyle` 以展示基础富文本样式。

### 5.3 图片与多媒体文件完全丢失 (Total Loss of Images & Multimedia)
* **涉及代码**：
  * [EpubTextExtractor.kt:L196](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/EpubTextExtractor.kt#L196) (`sanitizeHtml`)
  * [MainScreen.kt:L513-L543](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreen.kt#L513-L543) (`ReaderContent` 原生渲染)
* **问题描述**：
  * **图片过滤**：`HtmlCompat.fromHtml()` 过滤时若未提供 `Html.ImageGetter`，默认不会解析 `<img>` 标签，并且所有的图片占位符都会被随后的 `.toString()` 抹除。
  * **多媒体忽略**：EPUB 标准中允许包含的音频（`<audio>`）、视频（`<video>`）等媒介标签，在当前的 `paragraphs: List<String>` 数据模型中完全无法承载，在 UI 层的 Compose `Text`/`ClickableText` 原生文本组件中更没有对应的播放和渲染插槽。
  这会导致书中的所有配图、多媒体资源在界面上完全处于“人间蒸发”状态，完全无法正确显示图片和多媒体。
* **修复建议**：
  若使用原生渲染，必须将 `<img>` 标签映射为自定义排版树的“图片节点”，再流式从 EPUB 文件包（Zip）中将该图片解压转换为 Bitmap 喂给 Compose 的 `Image` 或 `AsyncImage`。但该方式对音频/视频等流媒体文件的支持开发代价极大。

### 5.4 翻页模式（Paged Mode）下的字符截断与主线程卡顿
* **涉及代码**：
  * [MainScreen.kt:L479-L483](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreen.kt#L479-L483) (`maxCharsPerPage` 计算)
  * [MainScreen.kt:L807](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/ui/main/MainScreen.kt#L807) (`buildPagedSegments`)
* **问题描述**：
  目前 Paged 模式翻页切片机制基于**字符数估算**（利用屏幕物理宽度/高度和字号比例算出一个固定字符容量进行硬截断切片）。
  这种做法极为脆弱且不准确：
  1. 它无法得知文本在界面排版折行后的准确像素高度，也完全忽略了图片的占用空间和段落内边距的影响。
  2. 当用户把字号或行高调大时，文本的高度会大幅膨胀，估算的单页字符在实际绘制时高度会溢出屏幕，导致**最下方的几行字被拦腰截断（Clip），甚至完全溢出看不见，造成用户阅读漏字**。
  3. `buildPagedSegments` 循环遍历和拼接在 Compose 主线程中完成。如果章节较长，它会引发主线程瞬间阻塞，翻页或切换章节时发生显著顿卡。
* **修复建议**：
  若要实现纯原生的 Paged 模式，必须使用 Compose 的 `TextMeasurer` 动态计算布局行数和每行排版的高度，当高度累计刚好触及屏幕边界时进行物理切页；或者改用成熟的 WebView 进行宿主排版。

### 5.5 缺乏本地 Web 容器托管（Web-based Serving Missing）
* **涉及代码**：
  * [EpubImportService.kt](file:///C:/Users/as705/Documents/EPUB%20READER%20APK/epub-reader-app/app/src/main/java/com/example/epubreader/storage/EpubImportService.kt) 等
* **问题描述**：
  EPUB 的 XHTML 中的多媒体与图片资源使用的是局部的相对物理路径（如 `<img src="../Images/pic.jpg">`）。
  如果要实现完美的排版、无缝的 CSS 精美渲染、以及无缝的多媒体/图片显示，**目前业界最成熟且性能最高的方式是使用 `WebView` 作为渲染媒介，并由内置的微型 Web 服务器提供 EPUB 解压后的静态文件托管**。
  目前项目中没有任何 Web 服务器（如 Ktor / AndroidAsync）或者 `WebViewAssetLoader` 的集成。没有这个本地 Web 路由，即便使用 WebView，也无法直接加载沙盒外受保护的相对路径多媒体文件，导致图片及媒体文件全部挂起。
* **终极建议（实现高精度、极速渲染的方案）**：
  要达到“大文件秒开 + 完美展现 CSS 排版 + 正确渲染多媒体/视频/音频 + 图片无缝解析”的商业级要求，建议弃用当前的 Compose 原生 `Text` 段落渲染，重构为 **WebView 渲染路线**：
  1. **引入本地微型服务器/静态托管容器**（如使用嵌入式 Ktor，或使用 `androidx.webkit.WebViewAssetLoader` 拦截 URL 请求）。
  2. **流式加载**：只在 WebView 中一次性 load 对应章节的 XHTML 相对路径，让 WebView 自带的 Chromium 内核去解码渲染 HTML/CSS、播放视频/音频和加载图片。
  3. **利用 CSS 翻页**：利用 CSS Column 属性（`column-width: 100vw; column-gap: 0px;`）来让 WebView 渲染出完美的左右翻页效果，实现任何大文件秒开，排版效果能够100%还原电子书原貌。
