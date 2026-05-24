package com.engreader.app.domain.repository

import com.engreader.app.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
  fun observeBooks(): Flow<List<Book>>

  fun observeBook(bookId: String): Flow<Book?>

  suspend fun getBook(bookId: String): Book?

  suspend fun upsertBook(book: Book)

  suspend fun upsertBooks(books: List<Book>)

  suspend fun removeBook(bookId: String)
}