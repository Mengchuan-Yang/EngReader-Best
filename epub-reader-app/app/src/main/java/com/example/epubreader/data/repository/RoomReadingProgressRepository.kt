package com.engreader.app.data.repository

import com.engreader.app.data.local.ReadingProgressDao
import com.engreader.app.domain.model.ReadingProgress
import com.engreader.app.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReadingProgressRepository(private val readingProgressDao: ReadingProgressDao) : ReadingProgressRepository {
  override fun observeProgress(bookId: String): Flow<List<ReadingProgress>> =
    readingProgressDao.observeByBookId(bookId).map { entities -> entities.map { it.toDomain() } }

  override fun observeCurrentProgress(bookId: String): Flow<ReadingProgress?> =
    readingProgressDao.observeCurrentByBookId(bookId).map { it?.toDomain() }

  override suspend fun getCurrentProgress(bookId: String): ReadingProgress? =
    readingProgressDao.getCurrentByBookId(bookId)?.toDomain()

  override suspend fun upsertProgress(progress: ReadingProgress) {
    readingProgressDao.upsert(progress.toEntity())
  }

  override suspend fun clearProgress(bookId: String) {
    readingProgressDao.deleteByBookId(bookId)
  }
}