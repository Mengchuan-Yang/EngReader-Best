package com.engreader.app.model

import kotlinx.serialization.Serializable

@Serializable
data class BookRecord(
  val id: String,
  val title: String,
  val author: String = "",
  val sourceUri: String,
  val importedAt: Long,
  val lastReadAt: Long,
  val totalChapters: Int = 0,
  val coverPath: String = "",
)

@Serializable
data class BookmarkRecord(
  val id: String,
  val bookId: String,
  val chapterIndex: Int,
  val chapterTitle: String,
  val createdAt: Long,
)

@Serializable
data class ReadingProgressRecord(
  val bookId: String,
  val chapterIndex: Int,
  val paragraphIndex: Int,
  val updatedAt: Long,
)

@Serializable
enum class ReaderMode {
  VERTICAL,
  PAGED,
}

@Serializable
enum class ThemeMode {
  SYSTEM,
  DAY,
  NIGHT,
}

@Serializable
enum class ShelfSortMode {
  RECENT,
  IMPORTED,
  TITLE,
}

@Serializable
enum class ShelfViewMode {
  GRID,
  LIST,
}

@Serializable
data class ReaderSettings(
  val fontScale: Float = 1f,
  val lineHeightScale: Float = 1.5f,
  val paragraphSpacingScale: Float = 1f,
  val marginScale: Float = 1f,
  val readerMode: ReaderMode = ReaderMode.VERTICAL,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val showAnnotations: Boolean = true,
  val repeatAnnotationMode: RepeatAnnotationMode = RepeatAnnotationMode.TAP_ONLY,
  val shelfSortMode: ShelfSortMode = ShelfSortMode.RECENT,
  val shelfViewMode: ShelfViewMode = ShelfViewMode.GRID,
)

@Serializable
enum class RepeatAnnotationMode {
  TAP_ONLY,
  CHAPTER_AUTO,
  BOOK_AUTO,
}

@Serializable
data class AnnotationRecord(
  val id: String,
  val bookId: String,
  val chapterIndex: Int,
  val paragraphIndex: Int = 0,
  val anchorText: String,
  val translation: String,
  val type: AnnotationType = AnnotationType.WORD,
  val createdAt: Long,
)

@Serializable
enum class AnnotationType {
  WORD,
  SENTENCE,
}

@Serializable
data class PersistedState(
  val books: List<BookRecord> = emptyList(),
  val bookmarks: List<BookmarkRecord> = emptyList(),
  val progress: List<ReadingProgressRecord> = emptyList(),
  val annotations: List<AnnotationRecord> = emptyList(),
  val settings: ReaderSettings = ReaderSettings(),
)

data class ChapterContent(
  val index: Int,
  val title: String,
  val paragraphs: List<String>,
  val styledParagraphs: List<androidx.compose.ui.text.AnnotatedString> = emptyList(),
)

data class ParsedBook(
  val bookTitle: String? = null,
  val author: String? = null,
  val chapters: List<ChapterContent>,
)
