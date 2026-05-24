package com.engreader.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import com.engreader.app.model.ChapterContent
import com.engreader.app.model.ParagraphSegment
import com.engreader.app.model.ParsedBook
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.domain.TOCReference
import io.documentnode.epub4j.epub.EpubReader
import java.io.File
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpubTextExtractor(
  private val context: Context,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun extractCover(uri: Uri, bookId: String): String =
    withContext(dispatcher) {
      runCatching {
        context.contentResolver.openInputStream(uri).use { stream ->
          checkNotNull(stream) { "Cannot open EPUB file" }
          val book = EpubReader().readEpub(stream)
          val coverImage = book.coverImage ?: return@runCatching ""
          val data = coverImage.data ?: return@runCatching ""
          if (data.isEmpty()) return@runCatching ""

          val coverDir = File(context.filesDir, "covers")
          if (!coverDir.exists()) coverDir.mkdirs()

          val ext = inferImageExtension(coverImage)
          val coverFile = File(coverDir, "${bookId}.$ext")

          val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
          if (bitmap != null) {
            val thumbWidth = 240
            val thumbHeight = (thumbWidth.toFloat() / bitmap.width * bitmap.height).toInt().coerceAtLeast(1)
            val thumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
            coverFile.outputStream().use { out ->
              thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (thumb != bitmap) thumb.recycle()
            bitmap.recycle()
          } else {
            coverFile.writeBytes(data)
          }
          coverFile.absolutePath
        }
      }.getOrDefault("")
    }

  private fun inferImageExtension(resource: Resource): String {
    val href = resource.href?.lowercase(Locale.US) ?: ""
    val mediaType = resource.mediaType?.name?.lowercase(Locale.US) ?: ""
    return when {
      href.endsWith(".png") || mediaType.contains("png") -> "png"
      href.endsWith(".webp") || mediaType.contains("webp") -> "webp"
      href.endsWith(".gif") || mediaType.contains("gif") -> "gif"
      else -> "jpg"
    }
  }

  suspend fun extractMetadata(uri: Uri): EpubMetadata =
    withContext(dispatcher) {
      context.contentResolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "Cannot open EPUB file" }
        val book = EpubReader().readEpub(stream)
        val (title, author) = readMetadataSafe(book, uri)
        EpubMetadata(title = title, author = author)
      }
    }

  suspend fun parseBook(uri: Uri, bookId: String = "", startChapter: Int = 0, maxChapters: Int = Int.MAX_VALUE): ParsedBook =
    withContext(dispatcher) {
      context.contentResolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "Cannot open EPUB file" }
        val book = EpubReader().readEpub(stream)
        val tocTitleMap = buildTocTitleMap(book.tableOfContents.tocReferences)

        // Get OPF base directory from container
        val opfBaseDir = runCatching {
          val inspector = EpubStructureInspector(context)
          val file = File(uri.path ?: "")
          inspector.readContainer(file)?.opfBaseDir ?: ""
        }.getOrDefault("")

        val imageMap = extractImages(book, bookId)

        val allChapters = mutableListOf<ChapterContent>()
        var chapterIndex = 0

        for (spineRef in book.spine.spineReferences) {
          val resource = spineRef.resource ?: continue
          if (!isHtmlResource(resource)) continue

          // Stable indexing: increment ONCE per HTML spine item
          val currentIdx = chapterIndex
          chapterIndex++

          val rawHtml = readResourceText(resource, resource.href.orEmpty())

          if (rawHtml.isBlank()) {
            // Generate placeholder for empty chapters to keep list dense
            allChapters += ChapterContent(
              index = currentIdx,
              title = defaultTitle(currentIdx),
              paragraphs = listOf(""),
              styledParagraphs = emptyList(),
              segments = emptyList(),
            )
            continue
          }

          // Store raw HTML for later parsing, but only parse chapters in range
          if (currentIdx in startChapter until (startChapter + maxChapters)) {
            val chapter = parseChapterContent(rawHtml, imageMap, tocTitleMap, currentIdx, resource.href.orEmpty(), opfBaseDir)
            allChapters += chapter
          } else {
            // Add placeholder for chapters outside range
            val tocTitle = tocTitleMap[normalizeHref(resource.href)]
            val headingTitle = extractHeadingFromHtml(rawHtml)
            val bestTitle = pickBestTitle(
              tocTitle = tocTitle,
              heading = headingTitle,
              firstParagraph = "",
              fallback = defaultTitle(currentIdx),
            )
            allChapters += ChapterContent(
              index = currentIdx,
              title = bestTitle,
              paragraphs = emptyList(),
              styledParagraphs = emptyList(),
              segments = emptyList(),
            )
          }
        }

        val (finalTitle, finalAuthor) = readMetadataSafe(book, uri)

        if (allChapters.isEmpty()) {
          ParsedBook(
            bookTitle = finalTitle,
            author = finalAuthor,
            chapters = listOf(ChapterContent(index = 0, title = "Chapter 1", paragraphs = listOf("No readable chapter found."))),
            totalChapters = 1,
          )
        } else {
          ParsedBook(
            bookTitle = finalTitle,
            author = finalAuthor,
            chapters = allChapters,
            totalChapters = chapterIndex,
          )
        }
      }
    }

  suspend fun preRenderAll(
    uri: Uri,
    bookId: String,
    onProgress: (Float) -> Unit,
  ): ParsedBook =
    withContext(dispatcher) {
      context.contentResolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "Cannot open EPUB file" }
        val book = EpubReader().readEpub(stream)
        val tocTitleMap = buildTocTitleMap(book.tableOfContents.tocReferences)

        // Get OPF base directory from container
        val opfBaseDir = runCatching {
          val inspector = EpubStructureInspector(context)
          val file = File(uri.path ?: "")
          inspector.readContainer(file)?.opfBaseDir ?: ""
        }.getOrDefault("")
        val imageMap = extractImages(book, bookId)

        val chapters = mutableListOf<ChapterContent>()
        var chapterIndex = 0
        val totalChapters = book.spine.spineReferences.count { ref ->
          ref.resource != null && isHtmlResource(ref.resource!!)
        }.coerceAtLeast(1)

        val startTime = System.currentTimeMillis()
        val maxDurationMs = 5 * 60 * 1000L

        for (spineRef in book.spine.spineReferences) {
          if (System.currentTimeMillis() - startTime > maxDurationMs) break
          val resource = spineRef.resource ?: continue
          if (!isHtmlResource(resource)) continue

          val currentIdx = chapterIndex
          chapterIndex++

          val rawHtml = readResourceText(resource, resource.href.orEmpty())
          if (rawHtml.isBlank()) continue

          chapters += parseChapterContent(rawHtml, imageMap, tocTitleMap, currentIdx, resource.href.orEmpty())
          onProgress(chapterIndex.toFloat() / totalChapters)
        }

        onProgress(1f)
        val (finalTitle, finalAuthor) = readMetadataSafe(book)
        ParsedBook(
          bookTitle = finalTitle,
          author = finalAuthor,
          chapters = chapters.ifEmpty {
            listOf(ChapterContent(index = 0, title = "Chapter 1", paragraphs = listOf("No readable chapter found.")))
          },
          totalChapters = chapterIndex.coerceAtLeast(1),
        )
      }
    }

  suspend fun parseChapterRange(uri: Uri, bookId: String = "", indices: IntRange): List<ChapterContent> =
    withContext(dispatcher) {
      context.contentResolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "Cannot open EPUB file" }
        val book = EpubReader().readEpub(stream)
        val tocTitleMap = buildTocTitleMap(book.tableOfContents.tocReferences)

        // Get OPF base directory from container
        val opfBaseDir = runCatching {
          val inspector = EpubStructureInspector(context)
          val file = File(uri.path ?: "")
          inspector.readContainer(file)?.opfBaseDir ?: ""
        }.getOrDefault("")
        val imageMap = extractImages(book, bookId)

        val results = mutableListOf<ChapterContent>()
        var chapterIndex = 0

        for (spineRef in book.spine.spineReferences) {
          val resource = spineRef.resource ?: continue
          if (!isHtmlResource(resource)) continue

          if (chapterIndex in indices) {
            val rawHtml = readResourceText(resource, resource.href.orEmpty())
            if (rawHtml.isNotBlank()) {
              results += parseChapterContent(rawHtml, imageMap, tocTitleMap, chapterIndex, resource.href.orEmpty())
            }
          }
          chapterIndex += 1
        }
        results
      }
    }

  private fun parseChapterContent(
    rawHtml: String,
    imageMap: Map<String, String>,
    tocTitleMap: Map<String, String>,
    chapterIndex: Int,
    resourceHref: String = "",
    opfBaseDir: String = "",
  ): ChapterContent {
    val augmentedMap = imageMap.toMutableMap()
    extractCssImages(rawHtml, augmentedMap)

    val htmlWithMarkers = replaceImgTags(rawHtml, augmentedMap, resourceHref, opfBaseDir)
    val text = sanitizeHtml(htmlWithMarkers)
    val paragraphs = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    val styledParagraphs = sanitizeHtmlStyledParagraphs(htmlWithMarkers)
    val segments = paragraphs.map { paragraphToSegments(it) }

    val tocTitle = tocTitleMap[normalizeHref(resourceHref)]
    val headingTitle = extractHeadingFromHtml(rawHtml)
    val firstParagraphTitle = paragraphs.firstOrNull()?.take(48).orEmpty()
    val bestTitle = pickBestTitle(
      tocTitle = tocTitle,
      heading = headingTitle,
      firstParagraph = firstParagraphTitle,
      fallback = defaultTitle(chapterIndex),
    )

    return ChapterContent(
      index = chapterIndex,
      title = bestTitle,
      paragraphs = paragraphs.ifEmpty { listOf(text.trim()) }.filter { it.isNotBlank() },
      styledParagraphs = styledParagraphs,
      segments = segments,
    )
  }

  private fun extractImages(book: io.documentnode.epub4j.domain.Book, bookId: String): Map<String, String> {
    val imageDir = File(context.filesDir, "images/${bookId.ifBlank { "unknown" }}")
    if (!imageDir.exists()) imageDir.mkdirs()
    val map = mutableMapOf<String, String>()

    fun processResource(resource: Resource) {
      val href = resource.href ?: return
      if (!isImageResource(resource)) return
      if (map.containsKey(href)) return
      val data = runCatching { resource.data }.getOrNull() ?: return
      if (data.isEmpty()) return

      val ext = inferImageExtension(resource)
      val fileName = "${normalizeHref(href).replace("/", "_").replace("\\", "_")}.$ext"
      val imageFile = File(imageDir, fileName)
      if (!imageFile.exists()) {
        imageFile.writeBytes(data)
      }
      map[href] = imageFile.absolutePath
      map[normalizeHref(href)] = imageFile.absolutePath
      val simpleName = href.substringAfterLast('/')
      map[simpleName] = imageFile.absolutePath
      map["../Images/$simpleName"] = imageFile.absolutePath
    }

    // Collect images from spine references
    for (spineRef in book.spine.spineReferences) {
      spineRef.resource?.let { processResource(it) }
    }

    // Try to access OPF manifest resources via reflection
    runCatching {
      val resourcesField: Field = book.javaClass.getDeclaredField("resources")
      resourcesField.isAccessible = true
      val resourcesObj = resourcesField.get(book)
      if (resourcesObj != null) {
        val getResourcesMethod = resourcesObj.javaClass.getDeclaredMethod("getResources")
        val allResources = getResourcesMethod.invoke(resourcesObj) as? Map<*, *>
        allResources?.values?.forEach { res ->
          if (res is Resource) processResource(res)
        }
      }
    }

    // Also check cover image
    book.coverImage?.let { processResource(it) }

    return map
  }

  private fun resolveEpubHref(
    opfBaseDir: String,
    currentResourceHref: String,
    relativeHref: String,
  ): String {
    // 1. External URLs pass through unchanged
    if (relativeHref.startsWith("http://") || relativeHref.startsWith("https://")) {
      return relativeHref
    }
    // 2. Remove fragment
    val noFragment = relativeHref.substringBefore('#')
    // 3. URL decode
    val decoded = runCatching { URLDecoder.decode(noFragment, "UTF-8") }.getOrDefault(noFragment)
    // 4. If absolute EPUB path, strip leading /
    if (decoded.startsWith("/")) return decoded.trimStart('/').lowercase(Locale.US)
    // 5. If relative, resolve against current resource's directory
    val currentDir = currentResourceHref.substringBeforeLast('/').let { if (it == currentResourceHref) "" else it }
    val base = if (currentDir.isNotBlank()) "$currentDir/" else ""
    val combined = "$base$decoded"
    // 6. Normalize . and ..
    val parts = combined.split('/').toMutableList()
    val normalized = mutableListOf<String>()
    for (part in parts) {
      when (part) {
        "." -> {} // skip
        ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
        else -> normalized.add(part)
      }
    }
    return normalized.joinToString("/").lowercase(Locale.US)
  }

  private fun replaceImgTags(raw: String, imageMap: Map<String, String>, resourceHref: String = "", opfBaseDir: String = ""): String {
    if (imageMap.isEmpty()) return raw
    val imgRegex = Regex("<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
    return imgRegex.replace(raw) { match ->
      val src = match.groupValues[1]
      // Resolve path using OPF base + current resource
      val resolved = if (opfBaseDir.isNotBlank() || resourceHref.isNotBlank()) {
        resolveEpubHref(opfBaseDir, resourceHref, src)
      } else {
        normalizeHref(src)
      }
      val simpleName = src.substringAfterLast('/')
      val simpleResolved = resolved.substringAfterLast('/')
      val localPath = imageMap[src]
        ?: imageMap[resolved]
        ?: imageMap[normalizeHref(src)]
        ?: imageMap[simpleName]
        ?: imageMap[simpleResolved]
        ?: imageMap["../Images/$simpleName"]
        ?: imageMap["images/$simpleName"]
        ?: imageMap.entries.firstOrNull { it.key.endsWith(simpleName) }?.value
        ?: imageMap.entries.firstOrNull { it.key.endsWith(simpleResolved) }?.value
      if (localPath != null) {
        val altMatch = Regex("alt=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(match.value)
        val alt = altMatch?.groupValues?.getOrNull(1)?.replace("|", "") ?: ""
        "[[IMG:$localPath|$alt]]"
      } else {
        ""
      }
    }
  }

  private fun paragraphToSegments(text: String): List<ParagraphSegment> {
    val markerRegex = Regex("\\[\\[IMG:([^|]+)\\|([^\\]]*)\\]\\]")
    val segments = mutableListOf<ParagraphSegment>()
    var remaining = text
    while (remaining.isNotEmpty()) {
      val match = markerRegex.find(remaining)
      if (match == null) {
        val trimmed = remaining.trim()
        if (trimmed.isNotBlank()) {
          segments += ParagraphSegment.TextSegment(trimmed)
        }
        break
      }
      // Text before the image
      val before = remaining.substring(0, match.range.first).trim()
      if (before.isNotBlank()) {
        segments += ParagraphSegment.TextSegment(before)
      }
      // The image
      val imagePath = match.groupValues[1]
      val altText = match.groupValues[2]
      segments += ParagraphSegment.ImageSegment(imagePath = imagePath, altText = altText)
      remaining = remaining.substring(match.range.last + 1)
    }
    if (segments.isEmpty()) {
      segments += ParagraphSegment.TextSegment(text.trim())
    }
    return segments
  }

  private fun isImageResource(resource: Resource): Boolean {
    val href = resource.href ?: return false
    // Normalize the href
    val decodedUrl = runCatching { URLDecoder.decode(href, StandardCharsets.UTF_8.name()) }.getOrDefault(href)
    val lower = decodedUrl.lowercase(Locale.US)
    val mediaType = resource.mediaType?.name?.lowercase(Locale.US) ?: ""
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
      lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") ||
      lower.endsWith(".svg") ||
      mediaType.contains("image")
  }

  private fun buildTocTitleMap(references: List<TOCReference>): Map<String, String> {
    val map = linkedMapOf<String, String>()
    fun visit(list: List<TOCReference>) {
      list.forEach { ref ->
        val rawHref = ref.resource?.href
        val title = sanitizeMetadataText(ref.title)
        if (!rawHref.isNullOrBlank() && title.isNotBlank()) {
          // Store both full href (with fragment) and base href (without fragment)
          map[normalizeHref(rawHref)] = title
          // Also store with fragment for exact anchor matching
          val base = normalizeHref(rawHref.substringBefore('#'))
          val fragment = rawHref.substringAfter('#', "").trim()
          if (base.isNotBlank()) {
            map[base] = title
            // Store fragment-level mapping
            if (fragment.isNotBlank()) {
              map["$base#$fragment"] = title
            }
          }
        }
        if (ref.children.isNotEmpty()) visit(ref.children)
      }
    }
    visit(references)
    return map
  }

  private fun pickBestTitle(tocTitle: String?, heading: String?, firstParagraph: String, fallback: String): String {
    val candidates = listOf(tocTitle, heading, firstParagraph)
      .map { it?.trim().orEmpty() }
      .filter { it.isNotBlank() }
      .filterNot { looksLikeHashOrId(it) }
    return candidates.firstOrNull() ?: fallback
  }

  private fun extractHeadingFromHtml(rawHtml: String): String {
    val h1 = Regex("<h1[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.getOrNull(1).orEmpty()
    if (h1.isNotBlank()) return sanitizeHtml(h1).take(48)
    val title = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.getOrNull(1).orEmpty()
    return sanitizeHtml(title).take(48)
  }

  private fun sanitizeMetadataText(raw: String?): String =
    raw.orEmpty().replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

  /**
   * Read metadata with encoding fallback using raw OPF bytes.
   * P1: epub4j may not decode OPF metadata correctly for non-UTF-8 EPUBs.
   */
  private fun readMetadataSafe(book: io.documentnode.epub4j.domain.Book, sourceUri: Uri? = null): Pair<String?, String?> {
    val epubTitle = sanitizeMetadataText(book.metadata.firstTitle)
    val epubAuthor = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" }?.trim().orEmpty()

    if (epubTitle.isNotBlank() && !looksLikeHashOrId(epubTitle)) {
      return epubTitle.takeIf { it.isNotBlank() } to epubAuthor.takeIf { it.isNotBlank() }
    }

    // Fallback: read OPF bytes from ZipFile via container.xml OPF path
    if (sourceUri != null) {
      return runCatching {
        val file = File(sourceUri.path ?: return@runCatching epubTitle to epubAuthor)
        val inspector = EpubStructureInspector(context)
        val opfInfo = inspector.readOpfInfo(file)
        if (opfInfo != null) {
          val opfTitle = sanitizeMetadataText(opfInfo.title)
          val opfAuthor = sanitizeMetadataText(opfInfo.creator)
          opfTitle.takeIf { it.isNotBlank() && !looksLikeHashOrId(it) } to
            opfAuthor.takeIf { it.isNotBlank() }
        } else epubTitle to epubAuthor
      }.getOrDefault(epubTitle to epubAuthor)
    }

    return epubTitle to epubAuthor
  }

  private fun sanitizeHtml(raw: String): String {
    val preprocessed = preprocessHtml(raw)
    val noScript = preprocessed.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
    val noStyle = noScript.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
    val spanned = HtmlCompat.fromHtml(noStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)
    return spanned.toString()
      .replace(" ", " ")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  private fun sanitizeHtmlStyledParagraphs(raw: String): List<AnnotatedString> {
    val preprocessed = preprocessHtml(raw)
    val noScript = preprocessed.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
    val noStyle = noScript.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
    val spanned = HtmlCompat.fromHtml(noStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val fullText = spanned.toString()
      .replace(" ", " ")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()

    // Robust: find all \n positions to split accurately, never by substring search
    val lineStarts = mutableListOf(0)
    for (i in fullText.indices) {
      if (fullText[i] == '\n') lineStarts.add(i + 1)
    }
    lineStarts.add(fullText.length)

    val result = mutableListOf<AnnotatedString>()
    for (i in 0 until lineStarts.size - 1) {
      val lineText = fullText.substring(lineStarts[i], lineStarts[i + 1]).trim()
      if (lineText.isNotBlank()) {
        result.add(buildStyledLine(spanned, lineText, lineStarts[i], lineStarts[i + 1]))
      }
    }
    return result
  }

  private fun buildStyledLine(spanned: Spanned, lineText: String, lineStart: Int, lineEnd: Int): AnnotatedString {
    val builder = AnnotatedString.Builder(lineText)
    // Track heading level: if the line is wrapped in a heading span, record it
    var headingLevel = 0 // 0=body, 1=h1, 2=h2, ...

    spanned.getSpans(lineStart, lineEnd, Any::class.java).forEach { span ->
      val spanStart = (spanned.getSpanStart(span) - lineStart).coerceIn(0, lineText.length)
      val spanEnd = (spanned.getSpanEnd(span) - lineStart).coerceIn(0, lineText.length)
      if (spanStart >= spanEnd) return@forEach

      when (span) {
        is StyleSpan -> {
          val fontWeight = if (span.style == Typeface.BOLD) FontWeight.Bold else FontWeight.Normal
          val fontStyle = if (span.style == Typeface.ITALIC) FontStyle.Italic else FontStyle.Normal
          builder.addStyle(SpanStyle(fontWeight = fontWeight, fontStyle = fontStyle), spanStart, spanEnd)
        }
        is UnderlineSpan -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), spanStart, spanEnd)
        is StrikethroughSpan -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), spanStart, spanEnd)
        is SuperscriptSpan -> builder.addStyle(SpanStyle(baselineShift = BaselineShift.Superscript), spanStart, spanEnd)
        is SubscriptSpan -> builder.addStyle(SpanStyle(baselineShift = BaselineShift.Subscript), spanStart, spanEnd)
        is ForegroundColorSpan -> {
          val rawColor = span.foregroundColor
          // Remap very dark colors (night mode readability)
          val color = if (android.graphics.Color.red(rawColor) < 30 &&
            android.graphics.Color.green(rawColor) < 30 &&
            android.graphics.Color.blue(rawColor) < 30) {
            // Near-black text — use default content color (caller will style)
            androidx.compose.ui.graphics.Color.Unspecified
          } else {
            androidx.compose.ui.graphics.Color(rawColor)
          }
          if (color != androidx.compose.ui.graphics.Color.Unspecified) {
            builder.addStyle(SpanStyle(color = color), spanStart, spanEnd)
          }
        }
        is AbsoluteSizeSpan -> {
          // Convert px to dp for device-independent heading detection
          val density = context.resources.displayMetrics.density
          val sizeDp = span.size / density
          if (sizeDp >= 20f) {
            headingLevel = 2
          } else if (sizeDp >= 16f) {
            headingLevel = if (headingLevel == 0) 3 else headingLevel
          }
        }
        is RelativeSizeSpan -> {
          val scale = span.sizeChange
          if (scale > 1.3f) headingLevel = maxOf(headingLevel, 2)
          else if (scale > 1.1f) headingLevel = maxOf(headingLevel, 3)
        }
        is URLSpan -> {
          builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = androidx.compose.ui.graphics.Color(0xFF1565C0)), spanStart, spanEnd)
          builder.addStringAnnotation(tag = "URL", annotation = span.url, start = spanStart, end = spanEnd)
        }
        is TypefaceSpan -> {
          val family = span.family?.lowercase(Locale.US) ?: ""
          if (family.contains("monospace") || family.contains("courier") || family.contains("mono")) {
            builder.addStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), spanStart, spanEnd)
          }
        }
        is LeadingMarginSpan -> {
          // Blockquote or list item
        }
        is QuoteSpan -> {
          builder.addStringAnnotation("BLOCKQUOTE", "1", spanStart, spanEnd)
        }
        is BulletSpan -> {
          // List item bullet — already handled by preprocessor, but mark it
        }
      }
    }

    val result = builder.toAnnotatedString()
    // Store heading level so rendering layer can adjust font size
    return if (headingLevel > 0) {
      AnnotatedString.Builder(result).apply {
        addStringAnnotation("HEADING", headingLevel.toString(), 0, result.text.length)
      }.toAnnotatedString()
    } else result
  }

  private fun readResourceText(resource: Resource, href: String): String {
    return runCatching {
      // Try reading raw bytes first for encoding detection
      val data = resource.data
      if (data != null && data.isNotEmpty()) {
        decodeBytes(data, href)
      } else {
        // Fall back to reader
        resource.reader.readText()
      }
    }.getOrElse { e ->
      Log.w("EpubTextExtractor", "Failed to read $href: ${e.message}", e)
      ""
    }
  }

  private fun decodeStrict(data: ByteArray, charset: Charset): String? {
    return runCatching {
      charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(data))
        .toString()
    }.getOrNull()
  }

  private fun decodeBytes(data: ByteArray, href: String): String {
    // 1. Check BOM
    if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) {
      return String(data, 3, data.size - 3, Charsets.UTF_8)
    }
    if (data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) {
      return String(data, 2, data.size - 2, Charsets.UTF_16BE)
    }
    if (data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) {
      return String(data, 2, data.size - 2, Charsets.UTF_16LE)
    }

    // 2. Try to find encoding from XML declaration in first 512 bytes
    val head = String(data, 0, data.size.coerceAtMost(512), Charsets.ISO_8859_1)
    val xmlEncPattern = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    val xmlEnc = xmlEncPattern.find(head)?.groupValues?.getOrNull(1)?.trim()

    // 3. Try to find charset from HTML meta in first 2048 bytes
    val htmlHead = String(data, 0, data.size.coerceAtMost(2048), Charsets.ISO_8859_1)
    val metaCharsetPattern = Regex("""<meta[^>]+charset\s*=\s*["']?([^"'\s;>]+)""", RegexOption.IGNORE_CASE)
    val metaCharset = metaCharsetPattern.find(htmlHead)?.groupValues?.getOrNull(1)?.trim()

    val declaredEncoding = xmlEnc ?: metaCharset

    // 4. Try declared encoding with strict decode
    if (declaredEncoding != null) {
      val normalized = declaredEncoding.lowercase(Locale.US).replace("-", "").replace("_", "")
      val charset = when (normalized) {
        "utf8" -> Charsets.UTF_8
        "utf16", "utf16be" -> Charsets.UTF_16BE
        "utf16le" -> Charsets.UTF_16LE
        "iso88591", "latin1" -> Charsets.ISO_8859_1
        "gb2312", "gbk", "gb18030" -> Charset.forName("GB18030")
        "shift_jis", "sjis", "windows31j" -> runCatching { Charset.forName("Shift_JIS") }.getOrElse { Charsets.UTF_8 }
        else -> runCatching { Charset.forName(declaredEncoding) }.getOrElse { Charsets.UTF_8 }
      }
      decodeStrict(data, charset)?.let { return it }
    }

    // 5. Try UTF-8 strict
    decodeStrict(data, Charsets.UTF_8)?.let { return it }

    // 6. Try GB18030 fallback
    runCatching { Charset.forName("GB18030") }.getOrNull()?.let { gb ->
      decodeStrict(data, gb)?.let { return it }
    }

    // 7. Last resort: ISO-8859-1 (never fails but may produce garbled text)
    Log.w("EpubTextExtractor", "Encoding fallback for $href: using ISO-8859-1")
    return String(data, Charsets.ISO_8859_1)
  }

  private fun isHtmlResource(resource: Resource): Boolean {
    val mediaType = resource.mediaType?.name?.lowercase(Locale.US) ?: ""
    // Primary: media-type check
    if (mediaType == "application/xhtml+xml" || mediaType == "text/html") return true
    // Fallback: href suffix
    val href = resource.href?.lowercase(Locale.US) ?: return false
    return href.endsWith(".xhtml") || href.endsWith(".html") || href.endsWith(".htm")
  }

  private fun normalizeHref(rawHref: String): String {
    val noFragment = rawHref.substringBefore('#').trim().trimStart('/')
    val decoded = runCatching { URLDecoder.decode(noFragment, StandardCharsets.UTF_8.name()) }.getOrDefault(noFragment)
    return decoded.removePrefix("./").lowercase(Locale.US)
  }

  private fun preprocessHtml(raw: String): String {
    var html = raw
    // 1. Convert <table> blocks to indented text representation
    html = html.replace(Regex("<table[^>]*>[\\s\\S]*?</table>", RegexOption.IGNORE_CASE)) { match ->
      tableToText(match.value)
    }
    // 2. Add bullet prefix for <ul> list items
    html = html.replace(Regex("<ul[^>]*>[\\s\\S]*?</ul>", RegexOption.IGNORE_CASE)) { match ->
      match.value.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "<li>• ")
    }
    // 3. Add numbered prefix for <ol> list items
    var olCounter = 0
    html = html.replace(Regex("<ol[^>]*>[\\s\\S]*?</ol>", RegexOption.IGNORE_CASE)) { match ->
      olCounter = 0
      match.value.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE)) {
        olCounter++
        "<li>$olCounter. "
      }
    }
    return html
  }

  private fun extractCssImages(raw: String, imageMap: MutableMap<String, String>) {
    val cssUrlRegex = Regex("""background-image\s*:\s*url\s*\(\s*["']?([^)"'\s]+)["']?\s*\)""", RegexOption.IGNORE_CASE)
    val styleBlocks = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE).findAll(raw)
    for (block in styleBlocks) {
      cssUrlRegex.findAll(block.value).forEach { match ->
        val url = match.groupValues[1]
        val normalized = normalizeHref(url)
        val simpleName = url.substringAfterLast('/')
        // Try to resolve against existing imageMap entries
        val resolved = imageMap[url]
          ?: imageMap[normalized]
          ?: imageMap[simpleName]
          ?: imageMap["../Images/$simpleName"]
          ?: imageMap["images/$simpleName"]
          ?: imageMap.entries.firstOrNull { it.key.endsWith(simpleName) }?.value
        if (resolved != null) {
          imageMap[url] = resolved
          imageMap[normalized] = resolved
        }
      }
    }
  }

  private fun tableToText(tableHtml: String): String {
    val sb = StringBuilder()
    sb.append("\n")
    val rows = Regex("<tr[^>]*>[\\s\\S]*?</tr>", RegexOption.IGNORE_CASE).findAll(tableHtml).toList()
    if (rows.isEmpty()) return "\n[Table]\n"
    for (row in rows) {
      val cells = Regex("<t[hd][^>]*>[\\s\\S]*?</t[hd]>", RegexOption.IGNORE_CASE).findAll(row.value).toList()
      val cellTexts = cells.map { cell ->
        // Extract text preserving inline tags like <b>, <i>, <a>
        val inner = cell.value
          .replace(Regex("</?t[hd][^>]*>", RegexOption.IGNORE_CASE), "")
          .replace(Regex("<[^>]+>")) { match ->
            val tag = match.value.lowercase(Locale.US)
            when {
              tag.contains("</b>") || tag.contains("</strong>") -> ""
              tag.contains("</i>") || tag.contains("</em>") -> ""
              tag.contains("</a>") -> ""
              tag.contains("<br") -> " "
              else -> ""
            }
          }
          .trim().take(40)
 inner
      }
      if (cellTexts.isNotEmpty()) {
        sb.append("  ${cellTexts.joinToString(" │ ")}\n")
      }
    }
    sb.append("\n")
    return sb.toString()
  }

  private fun looksLikeHashOrId(text: String): Boolean {
    val compact = text.trim()
    if (compact.length < 8) return false
    if (compact.any { it > '' || it.isWhitespace() }) return false
    if (!compact.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }) return false
    val digitCount = compact.count { it.isDigit() }
    val lowerCount = compact.count { it in 'a'..'z' }
    val upperCount = compact.count { it in 'A'..'Z' }
    val total = compact.length
    val looksHex = compact.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (looksHex && total >= 32) return true
    if (looksHex && total >= 12 && digitCount.toFloat() / total >= 0.3f) return true
    if (lowerCount > 0 && upperCount > 0 && digitCount >= 3 && digitCount.toFloat() / total >= 0.2f) return true
    if (lowerCount + digitCount == total && upperCount == 0 && digitCount in 2..(total / 2)) return true
    return false
  }

  private fun defaultTitle(index: Int): String = "Chapter ${index + 1}"
}

data class EpubMetadata(
  val title: String? = null,
  val author: String? = null,
)
