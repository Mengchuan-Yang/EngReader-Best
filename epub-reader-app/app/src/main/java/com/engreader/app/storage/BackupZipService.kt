package com.engreader.app.storage

import android.content.Context
import android.net.Uri
import com.engreader.app.model.PersistedState
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class BackupZipService(
  private val context: Context,
  private val stateStore: AppStateStore,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

  private fun resolveBookFile(sourceUri: String): File? {
    return runCatching {
      val uri = Uri.parse(sourceUri)
      // Uri.fromFile() produces "file:///path" format; path gives the filesystem path
      val path = uri.path
      if (!path.isNullOrBlank()) {
        val file = File(path)
        if (file.exists()) return file
      }
      // Fallback: try booksDir with the filename
      val name = path?.substringAfterLast('/') ?: return null
      val fallback = File(booksDir, name)
      if (fallback.exists()) fallback else null
    }.getOrNull()
  }

  private val booksDir: File
    get() {
      val dir = File(context.filesDir, "books")
      if (!dir.exists()) dir.mkdirs()
      return dir
    }

  suspend fun exportToZip(outputUri: Uri): Int =
    withContext(dispatcher) {
      val snapshot = stateStore.snapshot()
      val resolver = context.contentResolver
      resolver.openOutputStream(outputUri, "w").use { output ->
        checkNotNull(output) { "Cannot write backup zip" }
        ZipOutputStream(output).use { zip ->
          zip.putNextEntry(ZipEntry("state.json"))
          zip.write(json.encodeToString(PersistedState.serializer(), snapshot).toByteArray())
          zip.closeEntry()

          var count = 0
          snapshot.books.forEach { book ->
            val sourceFile = resolveBookFile(book.sourceUri) ?: return@forEach
            if (sourceFile.exists()) {
              zip.putNextEntry(ZipEntry("books/${book.id}.epub"))
              sourceFile.inputStream().use { it.copyTo(zip) }
              zip.closeEntry()
              count += 1
            }
          }
          return@use count
        }
      } ?: 0
    }

  suspend fun restoreFromZip(inputUri: Uri): RestoreResult =
    withContext(dispatcher) {
      val resolver = context.contentResolver
      // 1.3 fix: Store temp file paths instead of ByteArrays to avoid OOM
      val tempFilesByBookId = linkedMapOf<String, File>()
      var restoredState: PersistedState? = null

      resolver.openInputStream(inputUri).use { input ->
        checkNotNull(input) { "Cannot open backup zip" }
        ZipInputStream(input).use { zip ->
          var entry = zip.nextEntry
          while (entry != null) {
            when {
              entry.name == "state.json" -> {
                restoredState = json.decodeFromString(PersistedState.serializer(), zip.readBytes().decodeToString())
              }
              entry.name.startsWith("books/") && entry.name.endsWith(".epub") -> {
                val id = entry.name.removePrefix("books/").removeSuffix(".epub")
                val tempFile = File(context.cacheDir, "restore_$id.epub")
                tempFile.outputStream().use { zip.copyTo(it) }
                tempFilesByBookId[id] = tempFile
              }
            }
            zip.closeEntry()
            entry = zip.nextEntry
          }
        }
      }

      val snapshot = restoredState ?: throw IOException("Invalid backup: missing state.json")

      // 1.4 fix: Write new books first, then clean up old ones (transactional safety)
      val rebuiltBooks = snapshot.books.mapNotNull { oldBook ->
        val tempFile = tempFilesByBookId[oldBook.id] ?: return@mapNotNull null
        val destFile = File(booksDir, "${oldBook.id}.epub")
        tempFile.copyTo(destFile, overwrite = true)
        tempFile.delete()
        oldBook.copy(sourceUri = Uri.fromFile(destFile).toString())
      }

      // Now safe to delete old books
      stateStore.snapshot().books.forEach { existingBook ->
        val oldFile = resolveBookFile(existingBook.sourceUri)
        oldFile?.delete()
      }

      stateStore.replaceAll(snapshot.copy(books = rebuiltBooks))
      RestoreResult(restoredBooks = rebuiltBooks.size)
    }
}

data class RestoreResult(
  val restoredBooks: Int,
)
