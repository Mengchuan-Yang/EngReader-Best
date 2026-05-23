package com.engreader.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.engreader.app.model.ChapterContent
import com.engreader.app.model.ParsedBook
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.domain.TOCReference
import io.documentnode.epub4j.epub.EpubReader
import java.io.File
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

          // Resize to thumbnail to save memory
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

  suspend fun parseBook(uri: Uri): ParsedBook =
    withContext(dispatcher) {
      context.contentResolver.openInputStream(uri).use { stream ->
        checkNotNull(stream) { "Cannot open EPUB file" }
        val book = EpubReader().readEpub(stream)
        val tocTitleMap = buildTocTitleMap(book.tableOfContents.tocReferences)

        val chapters = mutableListOf<ChapterContent>()
        var chapterIndex = 0

        for (spineRef in book.spine.spineReferences) {
          val resource = spineRef.resource ?: continue
          if (!isHtmlResource(resource)) continue

          val rawHtml = runCatching { resource.reader.readText() }.getOrDefault("")
          if (rawHtml.isBlank()) continue

          val text = sanitizeHtml(rawHtml)
          if (text.isBlank()) continue

          val paragraphs = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
          if (paragraphs.isEmpty()) continue

          val tocTitle = tocTitleMap[normalizeHref(resource.href)]
          val headingTitle = extractHeadingFromHtml(rawHtml)
          val firstParagraphTitle = paragraphs.firstOrNull()?.take(48).orEmpty()
          val bestTitle =
            pickBestTitle(
              tocTitle = tocTitle,
              heading = headingTitle,
              firstParagraph = firstParagraphTitle,
              fallback = defaultTitle(chapterIndex),
            )

          chapters += ChapterContent(index = chapterIndex, title = bestTitle, paragraphs = paragraphs)
          chapterIndex += 1
        }

        val metadataTitle = sanitizeMetadataText(book.metadata.firstTitle)
        val metadataAuthor = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" }?.trim().orEmpty()
        val finalTitle = metadataTitle.takeIf { it.isNotBlank() && !looksLikeHashOrId(it) }
        val finalAuthor = metadataAuthor.takeIf { it.isNotBlank() }

        if (chapters.isEmpty()) {
          ParsedBook(
            bookTitle = finalTitle,
            author = finalAuthor,
            chapters = listOf(ChapterContent(index = 0, title = "Chapter 1", paragraphs = listOf("No readable chapter found."))),
          )
        } else {
          ParsedBook(
            bookTitle = finalTitle,
            author = finalAuthor,
            chapters = chapters,
          )
        }
      }
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
        if (ref.children.isNotEmpty()) {
          visit(ref.children)
        }
      }
    }

    visit(references)
    return map
  }

  private fun pickBestTitle(
    tocTitle: String?,
    heading: String?,
    firstParagraph: String,
    fallback: String,
  ): String {
    val candidates = listOf(tocTitle, heading, firstParagraph)
      .map { it?.trim().orEmpty() }
      .filter { it.isNotBlank() }
      .filterNot { looksLikeHashOrId(it) }
    return candidates.firstOrNull() ?: fallback
  }

  private fun extractHeadingFromHtml(rawHtml: String): String {
    val h1 = Regex("<h1[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.getOrNull(1).orEmpty()
    if (h1.isNotBlank()) {
      return sanitizeHtml(h1).take(48)
    }
    val title = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.getOrNull(1).orEmpty()
    return sanitizeHtml(title).take(48)
  }

  private fun sanitizeMetadataText(raw: String?): String {
    return raw.orEmpty()
      .replace('\u0000', ' ')
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private fun sanitizeHtml(raw: String): String {
    val noScript = raw.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
    val noStyle = noScript.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
    val plain = HtmlCompat.fromHtml(noStyle, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    return plain
      .replace("\u00A0", " ")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
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

  private fun looksLikeHashOrId(text: String): Boolean {
    val compact = text.trim()
    if (compact.length < 8) return false
    // Never flag text that contains non-ASCII (CJK, etc.)
    if (compact.any { it > '\u007f' || it.isWhitespace() }) return false
    // Only flag strings that are pure alphanumeric with underscore/hyphen
    if (!compact.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }) return false
    val digitCount = compact.count { it.isDigit() }
    val lowerCount = compact.count { it in 'a'..'z' }
    val upperCount = compact.count { it in 'A'..'Z' }
    val total = compact.length
    // Hex-like strings >= 32 chars (hash)
    val looksHex = compact.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (looksHex && total >= 32) return true
    if (looksHex && total >= 12 && digitCount.toFloat() / total >= 0.3f) return true
    // Mixed-case with high digit ratio (UUID-like)
    if (lowerCount > 0 && upperCount > 0 && digitCount >= 3 && digitCount.toFloat() / total >= 0.2f) return true
    // All lowercase alphanumeric >= 8 chars with digits mixed in (like "lglbu8k5")
    if (lowerCount + digitCount == total && upperCount == 0 && digitCount in 2..(total / 2)) return true
    return false
  }

  private fun defaultTitle(index: Int): String = "Chapter ${index + 1}"
}

data class EpubMetadata(
  val title: String? = null,
  val author: String? = null,
)

