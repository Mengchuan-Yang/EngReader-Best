package com.engreader.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
  @Query("SELECT * FROM books ORDER BY COALESCE(lastReadAt, importedAt) DESC")
  fun observeBooks(): Flow<List<BookEntity>>

  @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
  fun observeBook(bookId: String): Flow<BookEntity?>

  @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
  suspend fun getBook(bookId: String): BookEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBook(book: BookEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBooks(books: List<BookEntity>)

  @Delete
  suspend fun deleteBook(book: BookEntity)

  @Query("DELETE FROM books WHERE id = :bookId")
  suspend fun deleteBookById(bookId: String)

  @Query("SELECT * FROM books")
  suspend fun getAllBooks(): List<BookEntity>
}