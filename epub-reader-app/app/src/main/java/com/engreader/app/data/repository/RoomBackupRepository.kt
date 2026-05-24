package com.engreader.app.data.repository

import androidx.room.withTransaction
import com.engreader.app.data.local.EngReaderDatabase
import com.engreader.app.domain.model.EngReaderBackup
import com.engreader.app.domain.repository.BackupRepository

class RoomBackupRepository(private val database: EngReaderDatabase) : BackupRepository {
  override suspend fun exportBackup(): EngReaderBackup =
    EngReaderBackup(
      books = database.bookDao().getAllBooks().map { it.toDomain() },
      annotations = database.annotationDao().getAllAnnotations().map { it.toDomain() },
      bookmarks = database.bookmarkDao().getAllBookmarks().map { it.toDomain() },
      readingProgress = database.readingProgressDao().getAllProgress().map { it.toDomain() },
      settings = database.appSettingDao().getAllSettings().map { it.toDomain() }
    )

  override suspend fun importBackup(backup: EngReaderBackup) {
    database.withTransaction {
      database.bookDao().upsertBooks(backup.books.map { it.toEntity() })
      backup.annotations.forEach { database.annotationDao().insertAnnotation(it.toEntity()) }
      backup.bookmarks.forEach { database.bookmarkDao().insertBookmark(it.toEntity()) }
      backup.readingProgress.forEach { database.readingProgressDao().upsert(it.toEntity()) }
      backup.settings.forEach { database.appSettingDao().upsertSetting(it.toEntity()) }
    }
  }
}