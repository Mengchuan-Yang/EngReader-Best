package com.engreader.app.data.repository

import com.engreader.app.data.local.BookDao
import com.engreader.app.domain.model.Book
import com.engreader.app.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBookRepository(private val bookDao: BookDao) : BookRepository {
  override fun observeBooks(): Flow<List<Book>> =
    bookDao.observeBooks().map { entities -> entities.map { it.toDomain() } }

  override fun observeBook(bookId: String): Flow<Book?> =
    bookDao.observeBook(bookId).map { it?.toDomain() }

  override suspend fun getBook(bookId: String): Book? = bookDao.getBook(bookId)?.toDomain()

  override suspend fun upsertBook(book: Book) {
    bookDao.upsertBook(book.toEntity())
  }

  override suspend fun upsertBooks(books: List<Book>) {
    bookDao.upsertBooks(books.map { it.toEntity() })
  }

  override suspend fun removeBook(bookId: String) {
    bookDao.deleteBookById(bookId)
  }
}