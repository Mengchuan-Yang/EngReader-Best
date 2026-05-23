package com.engreader.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.engreader.app.model.PersistedState
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
            val source = Uri.parse(book.sourceUri)
            resolver.openInputStream(source)?.use { input ->
              zip.putNextEntry(ZipEntry("books/${book.id}.epub"))
              input.copyTo(zip)
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
      val payloadsByBookId = linkedMapOf<String, ByteArray>()
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
                payloadsByBookId[id] = zip.readBytes()
              }
            }
            zip.closeEntry()
            entry = zip.nextEntry
          }
        }
      }

      val snapshot = restoredState ?: throw IOException("Invalid backup: missing state.json")

      stateStore.snapshot().books.forEach { existingBook ->
        runCatching { resolver.delete(Uri.parse(existingBook.sourceUri), null, null) }
      }

      val rebuiltBooks =
        snapshot.books.mapNotNull { oldBook ->
          val bytes = payloadsByBookId[oldBook.id] ?: return@mapNotNull null
          val newUri = writeBookToPublicStorage(oldBook.title, bytes)
          oldBook.copy(sourceUri = newUri.toString())
        }

      stateStore.replaceAll(snapshot.copy(books = rebuiltBooks))
      RestoreResult(restoredBooks = rebuiltBooks.size)
    }

  private fun writeBookToPublicStorage(title: String, bytes: ByteArray): Uri {
    val values =
      ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.epub")
        put(MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/EngReader/books")
      }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: error("Cannot create restored book")
    resolver.openOutputStream(uri, "w").use { output ->
      checkNotNull(output) { "Cannot open restored book output" }
      output.write(bytes)
      output.flush()
    }
    return uri
  }
}

data class RestoreResult(
  val restoredBooks: Int,
)
