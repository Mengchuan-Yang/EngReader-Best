package com.engreader.app.data.repository

import com.engreader.app.data.local.BookmarkDao
import com.engreader.app.domain.model.Bookmark
import com.engreader.app.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBookmarkRepository(private val bookmarkDao: BookmarkDao) : BookmarkRepository {
  override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> =
    bookmarkDao.observeBookmarks(bookId).map { entities -> entities.map { it.toDomain() } }

  override suspend fun getBookmarks(bookId: String): List<Bookmark> =
    bookmarkDao.getBookmarks(bookId).map { it.toDomain() }

  override suspend fun addBookmark(bookmark: Bookmark): Long = bookmarkDao.insertBookmark(bookmark.toEntity())

  override suspend fun removeBookmark(bookmarkId: Long) {
    bookmarkDao.deleteById(bookmarkId)
  }

  override suspend fun clearBookmarks(bookId: String) {
    bookmarkDao.deleteByBookId(bookId)
  }
}