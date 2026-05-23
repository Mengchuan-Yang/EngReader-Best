package com.engreader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
  fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

  @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
  suspend fun getBookmarks(bookId: String): List<BookmarkEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBookmark(bookmark: BookmarkEntity): Long

  @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
  suspend fun deleteById(bookmarkId: Long)

  @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
  suspend fun deleteByBookId(bookId: String)

  @Query("SELECT * FROM bookmarks")
  suspend fun getAllBookmarks(): List<BookmarkEntity>
}