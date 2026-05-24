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
        val title = sanitizeMetadataText(book.metadata.firstTitle)
        val author = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" }?.trim().orEmpty()
        EpubMetadata(
          title = title.takeIf { it.isNotBlank() && !looksLikeHashOrId(it) },
          author = author.takeIf { it.isNotBlank() },
        )
      }
    }

  suspend fun parseBook(uri: Uri, bookId: String = "", startChapter: Int = 0, maxChapters: Int = Int.MAX_VALUE): ParsedBook =
    withContext(dispatcher) {
      context.contentResolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "Cannot open EPUB file" }
        val book = EpubReader().readEpub(stream)
        val tocTitleMap = buildTocTitleMap(book.tableOfContents.tocReferences)

        val imageMap = extractImages(book, bookId)

        val allChapters = mutableListOf<ChapterContent>()
        var chapterIndex = 0

        for (spineRef in book.spine.spineReferences) {
          val resource = spineRef.resource ?: continue
          if (!isHtmlResource(resource)) continue

          val rawHtml = runCatching { resource.reader.readText() }.getOrDefault("")
          if (rawHtml.isBlank()) continue

          // Store raw HTML for later parsing, but only parse chapters in range
          if (chapterIndex in startChapter until (startChapter + maxChapters)) {
            val chapter = parseChapterContent(rawHtml, imageMap, tocTitleMap, chapterIndex, resource.href.orEmpty())
            allChapters += chapter
          } else {
            // Add placeholder for chapters outside range
            val tocTitle = tocTitleMap[normalizeHref(resource.href)]
            val headingTitle = extractHeadingFromHtml(rawHtml)
            val bestTitle = pickBestTitle(
              tocTitle = tocTitle,
              heading = headingTitle,
              firstParagraph = "",
              fallback = defaultTitle(chapterIndex),
            )
            allChapters += ChapterContent(
              index = chapterIndex,
              title = bestTitle,
              paragraphs = emptyList(),
              styledParagraphs = emptyList(),
              segments = emptyList(),
            )
          }
          chapterIndex += 1
        }

        val metadataTitle = sanitizeMetadataText(book.metadata.firstTitle)
        val metadataAuthor = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" }?.trim().orEmpty()
        val finalTitle = metadataTitle.takeIf { it.isNotBlank() && !looksLikeHashOrId(it) }
        val finalAuthor = metadataAuthor.takeIf { it.isNotBlank() }

        if (allChapters.isEmpty()) {
          ParsedBook(
            bookTitle = finalTitle,
            author = finalAuthor,
            chapters = listOf(ChapterContent(index = 0, title = "Chapter 1", paragraphs = listOf("No readable chapter found."))),
          )
        } else {
          ParsedBook(
            bookTitle = finalTitle,
            author = finalAuthor,
            chapters = allChapters,
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

          val rawHtml = runCatching { resource.reader.readText() }.getOrDefault("")
          if (rawHtml.isBlank()) { chapterIndex++; continue }

          chapters += parseChapterContent(rawHtml, imageMap, tocTitleMap, chapterIndex, resource.href.orEmpty())
          chapterIndex++
          onProgress(chapterIndex.toFloat() / totalChapters)
        }

        onProgress(1f)
        val metadataTitle = sanitizeMetadataText(book.metadata.firstTitle)
        val metadataAuthor = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" }?.trim().orEmpty()
        ParsedBook(
          bookTitle = metadataTitle.takeIf { it.isNotBlank() && !looksLikeHashOrId(it) },
          author = metadataAuthor.takeIf { it.isNotBlank() },
          chapters = chapters.ifEmpty {
            listOf(ChapterContent(index = 0, title = "Chapter 1", paragraphs = listOf("No readable chapter found.")))
          },
        )
      }
    }

  suspend fun parseChapterRange(uri: Uri, bookId: String = "", indices: IntRange): List<ChapterContent> =
    withContext(dispatcher) {
      context.contentResolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "Cannot open EPUB file" }
        val book = EpubReader().readEpub(stream)
        val tocTitleMap = buildTocTitleMap(book.tableOfContents.tocReferences)
        val imageMap = extractImages(book, bookId)

        val results = mutableListOf<ChapterContent>()
        var chapterIndex = 0

        for (spineRef in book.spine.spineReferences) {
          val resource = spineRef.resource ?: continue
          if (!isHtmlResource(resource)) continue

          if (chapterIndex in indices) {
            val rawHtml = runCatching { resource.reader.readText() }.getOrDefault("")
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
  ): ChapterContent {
    val htmlWithMarkers = replaceImgTags(rawHtml, imageMap)
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

  private fun replaceImgTags(raw: String, imageMap: Map<String, String>): String {
    if (imageMap.isEmpty()) return raw
    val imgRegex = Regex("<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
    return imgRegex.replace(raw) { match ->
      val src = match.groupValues[1]
      val normalized = normalizeHref(src)
      val simpleName = src.substringAfterLast('/')
      // Try many path variants
      val localPath = imageMap[src]
        ?: imageMap[normalized]
        ?: imageMap[simpleName]
        ?: imageMap["../Images/$simpleName"]
        ?: imageMap["images/$simpleName"]
        ?: imageMap["Images/$simpleName"]
        ?: imageMap["image/$simpleName"]
        ?: imageMap["Image/$simpleName"]
        ?: imageMap["OEBPS/$simpleName"]
        ?: imageMap["OPS/$simpleName"]
        ?: imageMap.entries.firstOrNull { it.key.endsWith(simpleName) }?.value
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
        val href = ref.resource?.href
        val title = sanitizeMetadataText(ref.title)
        if (!href.isNullOrBlank() && title.isNotBlank()) {
          map[normalizeHref(href)] = title
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
        is ForegroundColorSpan -> builder.addStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(span.foregroundColor)), spanStart, spanEnd)
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

  private fun isHtmlResource(resource: Resource): Boolean {
    val href = resource.href ?: return false
    val lower = href.lowercase(Locale.US)
    return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
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
    // Scan inline <style> blocks for background-image URLs
    val cssUrlRegex = Regex("""background-image\s*:\s*url\s*\(\s*["']?([^)"'\s]+)["']?\s*\)""", RegexOption.IGNORE_CASE)
    val styleBlocks = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE).findAll(raw)
    val bookId = "css" // We don't have bookId here; images are already extracted by extractImages
    for (block in styleBlocks) {
      cssUrlRegex.findAll(block.value).forEach { match ->
        val url = match.groupValues[1]
        // Try to find this URL in the image map or nearby paths
        imageMap.computeIfAbsent(url) { url }
        val simpleName = url.substringAfterLast('/')
        imageMap.computeIfAbsent(simpleName) { simpleName }
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
