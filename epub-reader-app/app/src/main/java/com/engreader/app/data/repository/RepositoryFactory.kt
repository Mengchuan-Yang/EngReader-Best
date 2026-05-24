package com.engreader.app.data.repository

import com.engreader.app.data.local.EngReaderDatabase
import com.engreader.app.domain.repository.AnnotationRepository
import com.engreader.app.domain.repository.AppSettingsRepository
import com.engreader.app.domain.repository.BackupRepository
import com.engreader.app.domain.repository.BookRepository
import com.engreader.app.domain.repository.BookmarkRepository
import com.engreader.app.domain.repository.ReadingProgressRepository

data class EngReaderRepositories(
  val books: BookRepository,
  val annotations: AnnotationRepository,
  val bookmarks: BookmarkRepository,
  val progress: ReadingProgressRepository,
  val settings: AppSettingsRepository,
  val backup: BackupRepository
)

fun createRoomRepositories(database: EngReaderDatabase): EngReaderRepositories {
  return EngReaderRepositories(
    books = RoomBookRepository(database.bookDao()),
    annotations = RoomAnnotationRepository(database.annotationDao()),
    bookmarks = RoomBookmarkRepository(database.bookmarkDao()),
    progress = RoomReadingProgressRepository(database.readingProgressDao()),
    settings = RoomAppSettingsRepository(database.appSettingDao()),
    backup = RoomBackupRepository(database)
  )
}