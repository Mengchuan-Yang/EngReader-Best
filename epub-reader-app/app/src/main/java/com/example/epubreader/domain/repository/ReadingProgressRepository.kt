package com.engreader.app.domain.repository

import com.engreader.app.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface ReadingProgressRepository {
  fun observeProgress(bookId: String): Flow<List<ReadingProgress>>

  fun observeCurrentProgress(bookId: String): Flow<ReadingProgress?>

  suspend fun getCurrentProgress(bookId: String): ReadingProgress?

  suspend fun upsertProgress(progress: ReadingProgress)

  suspend fun clearProgress(bookId: String)
}