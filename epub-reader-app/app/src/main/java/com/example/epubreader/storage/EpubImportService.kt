package com.engreader.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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
  suspend fun importFromUri(inputUri: Uri): ImportResult =
    withContext(dispatcher) {
      val resolver = context.contentResolver
      val fileName = resolveFileName(inputUri)
      val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val finalName = ensureEpubExtension("${File(fileName).nameWithoutExtension}_$now")

      val values =
        ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
          put(MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
          put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/EngReader/books")
        }

      val destinationUri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
        ?: error("Unable to create destination file")

      resolver.openInputStream(inputUri).use { input ->
        checkNotNull(input) { "Cannot open source file" }
        resolver.openOutputStream(destinationUri, "w").use { output ->
          checkNotNull(output) { "Cannot open destination file" }
          input.copyTo(output)
        }
      }

      ImportResult(title = File(fileName).nameWithoutExtension.ifBlank { "Untitled" }, copiedUri = destinationUri)
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
