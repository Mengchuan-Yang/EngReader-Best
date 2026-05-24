package com.engreader.app.domain.repository

import com.engreader.app.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
  fun observeBookmarks(bookId: String): Flow<List<Bookmark>>

  suspend fun getBookmarks(bookId: String): List<Bookmark>

  suspend fun addBookmark(bookmark: Bookmark): Long

  suspend fun removeBookmark(bookmarkId: Long)

  suspend fun clearBookmarks(bookId: String)
}