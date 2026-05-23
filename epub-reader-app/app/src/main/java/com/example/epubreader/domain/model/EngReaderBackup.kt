package com.engreader.app.domain.model

data class EngReaderBackup(
  val books: List<Book>,
  val annotations: List<Annotation>,
  val bookmarks: List<Bookmark>,
  val readingProgress: List<ReadingProgress>,
  val settings: List<AppSetting>
)