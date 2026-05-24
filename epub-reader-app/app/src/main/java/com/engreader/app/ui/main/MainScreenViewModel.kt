package com.engreader.app.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.engreader.app.R
import com.engreader.app.ai.AiProvider
import com.engreader.app.ai.FallbackTranslationService
import com.engreader.app.ai.SentenceTranslationRequest
import com.engreader.app.ai.TranslationErrorKeys
import com.engreader.app.ai.TranslationResult
import com.engreader.app.ai.WordTranslationRequest
import com.engreader.app.model.AnnotationRecord
import com.engreader.app.model.AnnotationType
import com.engreader.app.model.BookRecord
import com.engreader.app.model.BookmarkRecord
import com.engreader.app.model.ChapterContent
import com.engreader.app.model.ReaderMode
import com.engreader.app.model.ReaderSettings
import com.engreader.app.model.ReadingProgressRecord
import com.engreader.app.model.RepeatAnnotationMode
import com.engreader.app.model.ShelfSortMode
import com.engreader.app.model.ShelfViewMode
import com.engreader.app.model.ThemeMode
import com.engreader.app.storage.AppStateStore
import com.engreader.app.storage.BackupZipService
import com.engreader.app.storage.EpubImportService
import com.engreader.app.storage.EpubTextExtractor
import com.engreader.app.settings.SecureApiKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
  private val stateStore = AppStateStore(application.applicationContext)
  private val backupZipService = BackupZipService(application.applicationContext, stateStore)
  private val importService = EpubImportService(application.applicationContext)
  private val textExtractor = EpubTextExtractor(application.applicationContext)
  private val secureApiKeyStore = SecureApiKeyStore(application.applicationContext)
  private val appContext = application.applicationContext
  private var lastTranslateEpochMs: Long = 0L

  private var cachedTranslationService: FallbackTranslationService? = null
  private fun getTranslationService(): FallbackTranslationService {
    val cached = cachedTranslationService
    if (cached != null) return cached
    val newService = FallbackTranslationService(secureApiKeyStore.loadSettings())
    cachedTranslationService = newService
    return newService
  }

  fun invalidateTranslationService() {
    cachedTranslationService = null
  }

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      combine(stateStore.books, stateStore.settings, stateStore.state) { books, settings, state ->
        Triple(books, settings, state.progress)
      }.collect { (books, settings, progress) ->
        _uiState.update {
          it.copy(
            books = sortBooks(books, settings.shelfSortMode),
            settings = settings,
            progressByBook = progress.associateBy { it.bookId },
            isLoading = false,
          )
        }
      }
    }
  }

  fun importFromUri(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, transientMessage = null) }
      runCatching {
          val result = importService.importFromUri(uri)
          val metadata = textExtractor.extractMetadata(result.copiedUri)
          val preferredTitle = metadata.title ?: result.title
          val book = stateStore.upsertBook(
            title = preferredTitle,
            sourceUri = result.copiedUri.toString(),
            author = metadata.author.orEmpty(),
          )
          // Extract cover immediately during import
          launch {
            val coverPath = textExtractor.extractCover(result.copiedUri, book.id)
            if (coverPath.isNotBlank()) {
              stateStore.updateBookMetadata(book.id, coverPath = coverPath)
            }
          }
          // Pre-render all chapters in background with progress
          launch {
            runCatching {
              textExtractor.preRenderAll(result.copiedUri, book.id) { progress ->
                stateStore.updateBookPreRender(book.id, progress)
                _uiState.update { it.copy(books = sortBooks(stateStore.snapshot().books, it.settings.shelfSortMode)) }
              }
            }
          }
        }
        .onSuccess {
          _uiState.update { it.copy(isLoading = false, transientMessage = appContext.getString(R.string.msg_import_success)) }
        }
        .onFailure { throwable ->
          _uiState.update { it.copy(isLoading = false, transientMessage = "导入失败: ${throwable.message ?: "unknown"}") }
        }
    }
  }

  fun clearTransientMessage() {
    _uiState.update { it.copy(transientMessage = null) }
  }

  fun openBook(book: BookRecord) {
    if (book.preRenderProgress > 0f && book.preRenderProgress < 1f) {
      _uiState.update { it.copy(transientMessage = "请等待预渲染完成") }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, transientMessage = null) }
      val initialCount = 5
      runCatching {
          // Load metadata and first few chapters quickly
          val parsed = textExtractor.parseBook(Uri.parse(book.sourceUri), book.id, 0, initialCount)
          if (!parsed.bookTitle.isNullOrBlank() || !parsed.author.isNullOrBlank()) {
            stateStore.updateBookMetadata(book.id, parsed.bookTitle, parsed.author, parsed.chapters.size)
          } else if (parsed.chapters.isNotEmpty()) {
            stateStore.updateBookMetadata(book.id, totalChapters = parsed.chapters.size)
          }
          // Extract cover on background
          launch {
            val coverPath = textExtractor.extractCover(Uri.parse(book.sourceUri), book.id)
            if (coverPath.isNotBlank()) {
              stateStore.updateBookMetadata(book.id, coverPath = coverPath)
            }
          }
          val effectiveBook = book.copy(title = parsed.bookTitle ?: book.title, author = parsed.author ?: book.author)
          val progress = stateStore.getProgress(book.id)
          val initialIndex = progress?.chapterIndex?.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0)) ?: 0
          val chapterSize = parsed.chapters.getOrNull(initialIndex)?.paragraphs?.size ?: 0
          val initialParagraphIndex = progress?.paragraphIndex?.coerceIn(0, (chapterSize - 1).coerceAtLeast(0)) ?: 0
          val readerState = ReaderUiState(
            book = effectiveBook,
            chapters = parsed.chapters,
            currentChapterIndex = initialIndex,
            currentParagraphIndex = initialParagraphIndex,
            bookmarks = stateStore.bookmarksForBook(book.id),
            annotations = stateStore.annotationsForBook(book.id),
          )
          readerState to parsed.chapters.size
        }
        .onSuccess { (reader, totalChapters) ->
          stateStore.updateLastRead(book.id)
          // Mark as pre-rendered for existing books
          if (book.preRenderProgress < 1f) {
            stateStore.updateBookPreRender(book.id, 1f)
          }
          _uiState.update { it.copy(isLoading = false, readerState = reader, screen = AppScreen.Reader) }

          // Background-load remaining chapters if needed
          if (totalChapters > initialCount) {
            launch {
              val remaining = textExtractor.parseChapterRange(
                Uri.parse(book.sourceUri), book.id, initialCount until totalChapters
              )
              if (remaining.isNotEmpty()) {
                _uiState.update {
                  val current = it.readerState ?: return@update it
                  val mergedChapters = current.chapters.toMutableList()
                  remaining.forEach { chapter ->
                    val idx = chapter.index
                    if (idx in mergedChapters.indices) {
                      mergedChapters[idx] = chapter
                    }
                  }
                  it.copy(readerState = current.copy(chapters = mergedChapters))
                }
              }
            }
          }
        }
        .onFailure { throwable ->
          _uiState.update { it.copy(isLoading = false, transientMessage = "打开失败: ${throwable.message ?: "unknown"}") }
        }
    }
  }

  fun backToShelf() {
    _uiState.update { it.copy(screen = AppScreen.Shelf, readerState = null) }
  }

  fun setChapter(index: Int) {
    setChapter(index, 0)
  }

  fun setChapter(index: Int, paragraphIndex: Int) {
    val reader = _uiState.value.readerState ?: return
    if (index !in reader.chapters.indices) return
    val boundedParagraphIndex = paragraphIndex.coerceIn(0, (reader.chapters[index].paragraphs.lastIndex).coerceAtLeast(0))
    _uiState.update {
      it.copy(
        readerState = reader.copy(currentChapterIndex = index, currentParagraphIndex = boundedParagraphIndex),
        scrollToChapterIndex = index,
      )
    }
    viewModelScope.launch {
      stateStore.saveProgress(reader.book.id, index, boundedParagraphIndex)
    }
  }

  fun clearScrollTarget() {
    _uiState.update { it.copy(scrollToChapterIndex = null) }
  }

  fun onChapterScrolledTo(chapterIndex: Int) {
    val reader = _uiState.value.readerState ?: return
    if (chapterIndex == reader.currentChapterIndex) return
    if (chapterIndex !in reader.chapters.indices) return
    _uiState.update {
      val current = it.readerState ?: return@update it
      it.copy(readerState = current.copy(currentChapterIndex = chapterIndex))
    }
    viewModelScope.launch {
      stateStore.saveProgress(reader.book.id, chapterIndex, 0)
    }
  }

  fun nextChapter() {
    val reader = _uiState.value.readerState ?: return
    setChapter((reader.currentChapterIndex + 1).coerceAtMost(reader.chapters.lastIndex))
  }

  fun prevChapter() {
    val reader = _uiState.value.readerState ?: return
    setChapter((reader.currentChapterIndex - 1).coerceAtLeast(0))
  }

  fun addBookmark() {
    val reader = _uiState.value.readerState ?: return
    val chapter = reader.currentChapter
    viewModelScope.launch {
      stateStore.addBookmark(reader.book.id, chapter.index, chapter.title)
        _uiState.update {
        val current = it.readerState ?: return@update it
        it.copy(
          readerState = current.copy(bookmarks = stateStore.bookmarksForBook(reader.book.id)),
          transientMessage = appContext.getString(R.string.msg_bookmark_added),
        )
      }
    }
  }

  fun removeBookmark(bookmarkId: String) {
    val reader = _uiState.value.readerState ?: return
    viewModelScope.launch {
      stateStore.removeBookmark(bookmarkId)
      _uiState.update {
        val current = it.readerState ?: return@update it
        it.copy(readerState = current.copy(bookmarks = stateStore.bookmarksForBook(reader.book.id)))
      }
    }
  }

  fun translateWordAt(chapterIndex: Int, paragraphIndex: Int, paragraph: String, charOffset: Int) {
    if (!allowTranslateNow()) return
    val reader = _uiState.value.readerState ?: return
    if (chapterIndex !in reader.chapters.indices) return
    val word = extractWordAtOffset(paragraph, charOffset) ?: return
    if (word.any { isCjk(it) }) {
      _uiState.update { it.copy(transientMessage = "当前内容无需翻译") }
      return
    }

    // 1.6 fix: Check local cache BEFORE making API call
    val repeatMode = _uiState.value.settings.repeatAnnotationMode
    if (repeatMode == RepeatAnnotationMode.TAP_ONLY) {
      val alreadyExists = stateStore.hasWordAnnotation(reader.book.id, chapterIndex, paragraphIndex, word)
      if (alreadyExists) {
        _uiState.update { it.copy(transientMessage = "该词已有翻译") }
        return
      }
    }

    val service = getTranslationService()
    viewModelScope.launch {
      when (val result = service.translateWord(WordTranslationRequest(word = word, contextSnippet = paragraph))) {
        is TranslationResult.Success -> {
          val translation = result.data.shortCn
          when (repeatMode) {
            RepeatAnnotationMode.TAP_ONLY -> {
              addWordAnnotationIfMissing(reader.book.id, chapterIndex, paragraphIndex, word, translation)
            }
            RepeatAnnotationMode.CHAPTER_AUTO -> {
              val batch = mutableListOf<AnnotationRecord>()
              reader.chapters.firstOrNull { it.index == chapterIndex }?.paragraphs?.forEachIndexed { idx, text ->
                if (containsWord(text, word) && !stateStore.hasWordAnnotation(reader.book.id, chapterIndex, idx, word)) {
                  batch += buildAnnotation(reader.book.id, chapterIndex, idx, word, translation)
                }
              }
              if (batch.isNotEmpty()) stateStore.addAnnotations(batch)
            }
            RepeatAnnotationMode.BOOK_AUTO -> {
              val batch = mutableListOf<AnnotationRecord>()
              reader.chapters.forEach { chapter ->
                chapter.paragraphs.forEachIndexed { idx, text ->
                  if (containsWord(text, word) && !stateStore.hasWordAnnotation(reader.book.id, chapter.index, idx, word)) {
                    batch += buildAnnotation(reader.book.id, chapter.index, idx, word, translation)
                  }
                }
              }
              if (batch.isNotEmpty()) stateStore.addAnnotations(batch)
            }
          }
          refreshReaderAnnotations(reader.book.id)
        }

        is TranslationResult.Failure -> {
          _uiState.update { it.copy(transientMessage = mapTranslationError(result.error.userErrorKey, result.error.debugMessage)) }
        }
      }
    }
  }

  fun translateSentenceAt(chapterIndex: Int, paragraphIndex: Int, paragraph: String) {
    if (!allowTranslateNow()) return
    val reader = _uiState.value.readerState ?: return
    if (chapterIndex !in reader.chapters.indices) return
    if (paragraph.any { isCjk(it) }) {
      _uiState.update { it.copy(transientMessage = "当前内容无需翻译") }
      return
    }

    // 1.6 fix: Check local cache BEFORE API call
    val alreadyTranslated = reader.annotations.any {
      it.chapterIndex == chapterIndex && it.paragraphIndex == paragraphIndex && it.type == AnnotationType.SENTENCE && it.anchorText == paragraph
    }
    if (alreadyTranslated) {
      _uiState.update { it.copy(transientMessage = "该句已有翻译") }
      return
    }

    val service = getTranslationService()
    viewModelScope.launch {
      when (val result = service.translateSentence(SentenceTranslationRequest(sentence = paragraph, contextSnippet = paragraph))) {
        is TranslationResult.Success -> {
          stateStore.addAnnotation(
            bookId = reader.book.id,
            chapterIndex = chapterIndex,
            paragraphIndex = paragraphIndex,
            anchorText = paragraph,
            translation = result.data.shortCn,
            type = AnnotationType.SENTENCE,
          )
          refreshReaderAnnotations(reader.book.id)
        }

        is TranslationResult.Failure -> {
          _uiState.update { it.copy(transientMessage = mapTranslationError(result.error.userErrorKey, result.error.debugMessage)) }
        }
      }
    }
  }

  fun removeAnnotation(annotationId: String) {
    val reader = _uiState.value.readerState ?: return
    viewModelScope.launch {
      stateStore.removeAnnotation(annotationId)
      refreshReaderAnnotations(reader.book.id)
    }
  }

  fun toggleAnnotationVisibility() {
    viewModelScope.launch {
      stateStore.updateSettings { settings ->
        settings.copy(showAnnotations = !settings.showAnnotations)
      }
    }
  }

  fun cycleRepeatAnnotationMode() {
    viewModelScope.launch {
      stateStore.updateSettings { settings ->
        val next =
          when (settings.repeatAnnotationMode) {
            RepeatAnnotationMode.TAP_ONLY -> RepeatAnnotationMode.CHAPTER_AUTO
            RepeatAnnotationMode.CHAPTER_AUTO -> RepeatAnnotationMode.BOOK_AUTO
            RepeatAnnotationMode.BOOK_AUTO -> RepeatAnnotationMode.TAP_ONLY
          }
        settings.copy(repeatAnnotationMode = next)
      }
    }
  }

  fun searchInCurrentBook(query: String) {
    val reader = _uiState.value.readerState ?: return
    if (query.isBlank()) {
      _uiState.update {
        val current = it.readerState ?: return@update it
        it.copy(readerState = current.copy(searchQuery = "", searchResults = emptyList()))
      }
      return
    }

    val normalized = query.trim().lowercase()
    val hits =
      buildList {
        reader.chapters.forEach { chapter ->
          chapter.paragraphs.forEachIndexed { paragraphIndex, text ->
            val position = text.lowercase().indexOf(normalized)
            if (position >= 0) {
              val snippetStart = (position - 20).coerceAtLeast(0)
              val snippetEnd = (position + normalized.length + 30).coerceAtMost(text.length)
              add(SearchHit(chapter.index, paragraphIndex, text.substring(snippetStart, snippetEnd)))
            }
          }
        }
      }

    _uiState.update {
      val current = it.readerState ?: return@update it
      it.copy(readerState = current.copy(searchQuery = query, searchResults = hits))
    }
  }

  fun jumpToSearchResult(hit: SearchHit) {
    setChapter(hit.chapterIndex, hit.paragraphIndex)
  }

  fun setCurrentParagraph(paragraphIndex: Int) {
    val reader = _uiState.value.readerState ?: return
    val bounded = paragraphIndex.coerceIn(0, (reader.currentChapter.paragraphs.lastIndex).coerceAtLeast(0))
    if (bounded == reader.currentParagraphIndex) return
    _uiState.update {
      val current = it.readerState ?: return@update it
      it.copy(readerState = current.copy(currentParagraphIndex = bounded))
    }
    viewModelScope.launch {
      stateStore.saveProgress(reader.book.id, reader.currentChapterIndex, bounded)
    }
  }

  fun toggleReaderMode() {
    viewModelScope.launch {
      stateStore.updateSettings { settings ->
        val mode = if (settings.readerMode == ReaderMode.VERTICAL) ReaderMode.PAGED else ReaderMode.VERTICAL
        settings.copy(readerMode = mode)
      }
    }
  }

  fun cycleThemeMode() {
    viewModelScope.launch {
      stateStore.updateSettings { settings ->
        val next =
          when (settings.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DAY
            ThemeMode.DAY -> ThemeMode.NIGHT
            ThemeMode.NIGHT -> ThemeMode.SYSTEM
          }
        settings.copy(themeMode = next)
      }
    }
  }

  fun toggleShelfViewMode() {
    viewModelScope.launch {
      stateStore.updateSettings { settings ->
        val next = if (settings.shelfViewMode == ShelfViewMode.GRID) ShelfViewMode.LIST else ShelfViewMode.GRID
        settings.copy(shelfViewMode = next)
      }
    }
  }

  fun cycleSortMode() {
    viewModelScope.launch {
      stateStore.updateSettings { settings ->
        val next =
          when (settings.shelfSortMode) {
            ShelfSortMode.RECENT -> ShelfSortMode.IMPORTED
            ShelfSortMode.IMPORTED -> ShelfSortMode.TITLE
            ShelfSortMode.TITLE -> ShelfSortMode.RECENT
          }
        settings.copy(shelfSortMode = next)
      }
    }
  }

  fun increaseFont() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(fontScale = (settings.fontScale + 0.1f).coerceAtMost(2f)) }
    }
  }

  fun decreaseFont() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(fontScale = (settings.fontScale - 0.1f).coerceAtLeast(0.8f)) }
    }
  }

  fun increaseLineHeight() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(lineHeightScale = (settings.lineHeightScale + 0.1f).coerceAtMost(2.2f)) }
    }
  }

  fun decreaseLineHeight() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(lineHeightScale = (settings.lineHeightScale - 0.1f).coerceAtLeast(1.1f)) }
    }
  }

  fun increaseParagraphSpacing() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(paragraphSpacingScale = (settings.paragraphSpacingScale + 0.2f).coerceAtMost(3f)) }
    }
  }

  fun decreaseParagraphSpacing() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(paragraphSpacingScale = (settings.paragraphSpacingScale - 0.2f).coerceAtLeast(0.5f)) }
    }
  }

  fun increaseMargin() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(marginScale = (settings.marginScale + 0.2f).coerceAtMost(3f)) }
    }
  }

  fun decreaseMargin() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(marginScale = (settings.marginScale - 0.2f).coerceAtLeast(0.5f)) }
    }
  }

  fun toggleJustify() {
    viewModelScope.launch {
      stateStore.updateSettings { settings -> settings.copy(justifyText = !settings.justifyText) }
    }
  }

  fun deleteBook(bookId: String) {
    viewModelScope.launch {
      stateStore.removeBook(bookId)
      _uiState.update { it.copy(transientMessage = appContext.getString(R.string.msg_book_deleted)) }
    }
  }

  fun saveApiKey(provider: AiProvider, apiKey: String) {
    viewModelScope.launch {
      if (apiKey.isBlank()) return@launch
      secureApiKeyStore.putApiKey(provider, apiKey.trim())
      invalidateTranslationService()
      _uiState.update { it.copy(transientMessage = appContext.getString(R.string.msg_api_key_saved)) }
    }
  }

  fun exportBackup(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching { backupZipService.exportToZip(uri) }
        .onSuccess { count ->
          _uiState.update { it.copy(isLoading = false, transientMessage = appContext.getString(R.string.msg_backup_exported, count)) }
        }
        .onFailure { throwable ->
          _uiState.update {
            it.copy(
              isLoading = false,
              transientMessage = appContext.getString(R.string.msg_backup_export_failed, throwable.message ?: "unknown"),
            )
          }
        }
    }
  }

  fun restoreBackup(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching { backupZipService.restoreFromZip(uri) }
        .onSuccess { result ->
          _uiState.update {
            it.copy(
              isLoading = false,
              transientMessage = appContext.getString(R.string.msg_backup_restored, result.restoredBooks),
            )
          }
        }
        .onFailure { throwable ->
          _uiState.update {
            it.copy(
              isLoading = false,
              transientMessage = appContext.getString(R.string.msg_backup_restore_failed, throwable.message ?: "unknown"),
            )
          }
        }
    }
  }

  private fun sortBooks(books: List<BookRecord>, sortMode: ShelfSortMode): List<BookRecord> {
    return when (sortMode) {
      ShelfSortMode.RECENT -> books.sortedByDescending { it.lastReadAt }
      ShelfSortMode.IMPORTED -> books.sortedByDescending { it.importedAt }
      ShelfSortMode.TITLE -> books.sortedBy { it.title.lowercase() }
    }
  }

  private fun refreshReaderAnnotations(bookId: String) {
    _uiState.update {
      val current = it.readerState ?: return@update it
      it.copy(readerState = current.copy(annotations = stateStore.annotationsForBook(bookId)))
    }
  }

  private fun extractWordAtOffset(text: String, offset: Int): String? {
    if (text.isEmpty() || offset !in text.indices) return null
    if (!text[offset].isLetter()) return null
    var start = offset
    var end = offset
    while (start > 0 && text[start - 1].isLetter()) start--
    while (end < text.lastIndex && text[end + 1].isLetter()) end++
    return text.substring(start, end + 1).trim().takeIf { it.isNotBlank() }
  }

  private fun isCjk(char: Char): Boolean = char.code in 0x4E00..0x9FFF

  private fun mapTranslationError(errorKey: String, debugMessage: String?): String {
    if (debugMessage?.contains("not configured", ignoreCase = true) == true) {
      return "API 未配置"
    }
    return when (errorKey) {
      TranslationErrorKeys.NETWORK -> "网络不可用"
      TranslationErrorKeys.TIMEOUT -> "请求超时"
      TranslationErrorKeys.API -> "翻译服务不可用"
      else -> "翻译失败"
    }
  }

  private fun allowTranslateNow(): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastTranslateEpochMs < 500) {
      return false
    }
    lastTranslateEpochMs = now
    return true
  }

  private suspend fun addWordAnnotationIfMissing(
    bookId: String,
    chapterIndex: Int,
    paragraphIndex: Int,
    word: String,
    translation: String,
  ) {
    if (stateStore.hasWordAnnotation(bookId, chapterIndex, paragraphIndex, word)) return
    stateStore.addAnnotation(
      bookId = bookId,
      chapterIndex = chapterIndex,
      paragraphIndex = paragraphIndex,
      anchorText = word,
      translation = translation,
      type = AnnotationType.WORD,
    )
  }

  private fun buildAnnotation(
    bookId: String,
    chapterIndex: Int,
    paragraphIndex: Int,
    word: String,
    translation: String,
  ): AnnotationRecord {
    return AnnotationRecord(
      id = java.util.UUID.randomUUID().toString(),
      bookId = bookId,
      chapterIndex = chapterIndex,
      paragraphIndex = paragraphIndex,
      anchorText = word,
      translation = translation,
      type = AnnotationType.WORD,
      createdAt = System.currentTimeMillis(),
    )
  }

  private fun containsWord(text: String, word: String): Boolean {
    val regex = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
    return regex.containsMatchIn(text)
  }
}

enum class AppScreen {
  Shelf,
  Reader,
}

data class ReaderUiState(
  val book: BookRecord,
  val chapters: List<ChapterContent>,
  val currentChapterIndex: Int,
  val currentParagraphIndex: Int = 0,
  val bookmarks: List<BookmarkRecord>,
  val annotations: List<AnnotationRecord>,
  val searchQuery: String = "",
  val searchResults: List<SearchHit> = emptyList(),
) {
  val currentChapter: ChapterContent
    get() = chapters[currentChapterIndex]
}

data class SearchHit(
  val chapterIndex: Int,
  val paragraphIndex: Int,
  val snippet: String,
)

data class MainUiState(
  val isLoading: Boolean = true,
  val books: List<BookRecord> = emptyList(),
  val settings: ReaderSettings = ReaderSettings(),
  val screen: AppScreen = AppScreen.Shelf,
  val readerState: ReaderUiState? = null,
  val transientMessage: String? = null,
  val progressByBook: Map<String, ReadingProgressRecord> = emptyMap(),
  val scrollToChapterIndex: Int? = null,
)
