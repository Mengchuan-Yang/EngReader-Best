package com.engreader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
  @Query("SELECT * FROM reading_progress WHERE bookId = :bookId ORDER BY lastUpdated DESC")
  fun observeByBookId(bookId: String): Flow<List<ReadingProgressEntity>>

  @Query("SELECT * FROM reading_progress WHERE bookId = :bookId ORDER BY lastUpdated DESC LIMIT 1")
  fun observeCurrentByBookId(bookId: String): Flow<ReadingProgressEntity?>

  @Query("SELECT * FROM reading_progress WHERE bookId = :bookId ORDER BY lastUpdated DESC LIMIT 1")
  suspend fun getCurrentByBookId(bookId: String): ReadingProgressEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(progress: ReadingProgressEntity)

  @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
  suspend fun deleteByBookId(bookId: String)

  @Query("SELECT * FROM reading_progress")
  suspend fun getAllProgress(): List<ReadingProgressEntity>
}