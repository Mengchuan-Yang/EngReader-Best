package com.engreader.app.storage

import android.content.Context
import com.engreader.app.model.BookRecord
import com.engreader.app.model.BookmarkRecord
import com.engreader.app.model.AnnotationRecord
import com.engreader.app.model.AnnotationType
import com.engreader.app.model.PersistedState
import com.engreader.app.model.ReaderSettings
import com.engreader.app.model.ReadingProgressRecord
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AppStateStore(
  context: Context,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val stateFile = File(context.filesDir, "engreader_state.json")
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

  private val _state = MutableStateFlow(loadFromDisk())
  val state: StateFlow<PersistedState> = _state.asStateFlow()

  val books: Flow<List<BookRecord>> = state.map { it.books }
  val settings: Flow<ReaderSettings> = state.map { it.settings }

  suspend fun upsertBook(title: String, sourceUri: String, author: String = ""): BookRecord =
    withContext(dispatcher) {
      val now = System.currentTimeMillis()
      val existing = _state.value.books.firstOrNull { it.sourceUri == sourceUri }
      val book =
        existing?.copy(title = title, author = author.ifBlank { existing.author }, lastReadAt = now)
          ?: BookRecord(
            id = UUID.randomUUID().toString(),
            title = title,
            author = author,
            sourceUri = sourceUri,
            importedAt = now,
            lastReadAt = now,
          )
      val nextBooks = _state.value.books.filterNot { it.id == book.id } + book
      persist(_state.value.copy(books = nextBooks))
      book
    }

  suspend fun updateBookMetadata(bookId: String, title: String? = null, author: String? = null, totalChapters: Int? = null, coverPath: String? = null) =
    withContext(dispatcher) {
      val nextBooks =
        _state.value.books.map { book ->
          if (book.id != bookId) {
            book
          } else {
            book.copy(
              title = title?.takeIf { it.isNotBlank() } ?: book.title,
              author = author?.takeIf { it.isNotBlank() } ?: book.author,
              totalChapters = totalChapters ?: book.totalChapters,
              coverPath = coverPath ?: book.coverPath,
            )
          }
        }
      persist(_state.value.copy(books = nextBooks))
    }

  suspend fun removeBook(bookId: String) =
    withContext(dispatcher) {
      val nextState =
        _state.value.copy(
          books = _state.value.books.filterNot { it.id == bookId },
          bookmarks = _state.value.bookmarks.filterNot { it.bookId == bookId },
          progress = _state.value.progress.filterNot { it.bookId == bookId },
          annotations = _state.value.annotations.filterNot { it.bookId == bookId },
        )
      persist(nextState)
    }

  suspend fun updateLastRead(bookId: String) =
    withContext(dispatcher) {
      val now = System.currentTimeMillis()
      val nextBooks = _state.value.books.map { if (it.id == bookId) it.copy(lastReadAt = now) else it }
      persist(_state.value.copy(books = nextBooks))
    }

  suspend fun saveProgress(bookId: String, chapterIndex: Int, paragraphIndex: Int) =
    withContext(dispatcher) {
      val now = System.currentTimeMillis()
      val item = ReadingProgressRecord(bookId = bookId, chapterIndex = chapterIndex, paragraphIndex = paragraphIndex, updatedAt = now)
      val next = _state.value.progress.filterNot { it.bookId == bookId } + item
      val nextBooks = _state.value.books.map { if (it.id == bookId) it.copy(lastReadAt = now) else it }
      persist(_state.value.copy(progress = next, books = nextBooks))
    }

  fun getProgress(bookId: String): ReadingProgressRecord? = _state.value.progress.firstOrNull { it.bookId == bookId }

  suspend fun addBookmark(bookId: String, chapterIndex: Int, chapterTitle: String): BookmarkRecord =
    withContext(dispatcher) {
      val item =
        BookmarkRecord(
          id = UUID.randomUUID().toString(),
          bookId = bookId,
          chapterIndex = chapterIndex,
          chapterTitle = chapterTitle,
          createdAt = System.currentTimeMillis(),
        )
      persist(_state.value.copy(bookmarks = _state.value.bookmarks + item))
      item
    }

  suspend fun removeBookmark(bookmarkId: String) =
    withContext(dispatcher) {
      persist(_state.value.copy(bookmarks = _state.value.bookmarks.filterNot { it.id == bookmarkId }))
    }

  fun bookmarksForBook(bookId: String): List<BookmarkRecord> = _state.value.bookmarks.filter { it.bookId == bookId }

  suspend fun addAnnotation(
    bookId: String,
    chapterIndex: Int,
    paragraphIndex: Int,
    anchorText: String,
    translation: String,
    type: AnnotationType,
  ): AnnotationRecord =
    withContext(dispatcher) {
      val item =
        AnnotationRecord(
          id = UUID.randomUUID().toString(),
          bookId = bookId,
          chapterIndex = chapterIndex,
          paragraphIndex = paragraphIndex,
          anchorText = anchorText,
          translation = translation,
          type = type,
          createdAt = System.currentTimeMillis(),
        )
      persist(_state.value.copy(annotations = _state.value.annotations + item))
      item
    }

  fun annotationsForBook(bookId: String): List<AnnotationRecord> = _state.value.annotations.filter { it.bookId == bookId }

  fun hasWordAnnotation(bookId: String, chapterIndex: Int, paragraphIndex: Int, anchorText: String): Boolean {
    return _state.value.annotations.any {
      it.bookId == bookId &&
        it.chapterIndex == chapterIndex &&
        it.paragraphIndex == paragraphIndex &&
        it.type == AnnotationType.WORD &&
        it.anchorText.equals(anchorText, ignoreCase = true)
    }
  }

  suspend fun removeAnnotation(annotationId: String): AnnotationRecord? =
    withContext(dispatcher) {
      val removed = _state.value.annotations.firstOrNull { it.id == annotationId }
      if (removed != null) {
        persist(_state.value.copy(annotations = _state.value.annotations.filterNot { it.id == annotationId }))
      }
      removed
    }

  suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) =
    withContext(dispatcher) {
      persist(_state.value.copy(settings = transform(_state.value.settings)))
    }

  fun snapshot(): PersistedState = _state.value

  suspend fun replaceAll(newState: PersistedState) =
    withContext(dispatcher) {
      persist(newState)
    }

  private fun persist(next: PersistedState) {
    _state.value = next
    stateFile.writeText(json.encodeToString(PersistedState.serializer(), next))
  }

  private fun loadFromDisk(): PersistedState {
    return runCatching {
        if (!stateFile.exists()) {
          PersistedState()
        } else {
          json.decodeFromString(PersistedState.serializer(), stateFile.readText())
        }
      }
      .getOrDefault(PersistedState())
  }
}
