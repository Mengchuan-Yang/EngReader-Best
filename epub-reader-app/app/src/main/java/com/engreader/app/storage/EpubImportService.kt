package com.engreader.app.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpubImportService(
  private val context: Context,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val inspector = EpubStructureInspector(context)

  suspend fun importFromUri(inputUri: Uri): ImportResult =
    withContext(dispatcher) {
      val resolver = context.contentResolver
      val fileName = resolveFileName(inputUri)

      // Pre-check file size via OpenableColumns.SIZE (more reliable than available())
      val fileSize = runCatching {
        resolver.query(inputUri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
      }.getOrDefault(0L)

      if (fileSize > 500 * 1024 * 1024) {
        throw IllegalStateException("File too large (${fileSize / 1024 / 1024}MB). Maximum 500MB.")
      }

      val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val finalName = ensureEpubExtension("${File(fileName).nameWithoutExtension}_$now")

      val booksDir = File(context.filesDir, "books")
      if (!booksDir.exists()) booksDir.mkdirs()
      val destFile = File(booksDir, finalName)

      // Single-pass: copy + validate with magic bytes inline
      var hasZipHeader = false
      resolver.openInputStream(inputUri).use { input ->
        checkNotNull(input) { "Cannot open source file" }
        val header = ByteArray(4)
        val read = input.read(header)
        if (read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
          hasZipHeader = true
        }
        destFile.outputStream().use { output ->
          output.write(header, 0, read)
          input.copyTo(output)
        }
      }

      if (!hasZipHeader) {
        destFile.delete()
        throw IllegalStateException("Not a valid EPUB file (missing ZIP header)")
      }

      // Validate EPUB structure on the copied file
      val validation = inspector.validate(destFile)
      if (!validation.isValid) {
        destFile.delete()
        throw IllegalStateException("Invalid EPUB: ${validation.errors.joinToString("; ")}")
      }

      ImportResult(
        title = File(fileName).nameWithoutExtension.ifBlank { "Untitled" },
        copiedUri = Uri.fromFile(destFile),
      )
    }

  private fun resolveFileName(uri: Uri): String {
    val mime = context.contentResolver.getType(uri)
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
    val fallback = "book_${System.currentTimeMillis()}${if (ext.isNullOrBlank()) ".epub" else ".$ext"}"
    val resolver = context.contentResolver
    val fromCursor =
      runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) cursor.getString(0) else null
        }
      }.getOrNull()
    return fromCursor?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: fallback
  }

  private fun ensureEpubExtension(stem: String): String {
    return if (stem.endsWith(".epub", ignoreCase = true)) stem else "$stem.epub"
  }
}

data class ImportResult(
  val title: String,
  val copiedUri: Uri,
)
