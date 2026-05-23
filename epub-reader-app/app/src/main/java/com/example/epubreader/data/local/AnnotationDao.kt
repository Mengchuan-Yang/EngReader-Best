package com.engreader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
  @Query("SELECT * FROM annotations WHERE bookId = :bookId AND (:includeHidden = 1 OR hidden = 0) ORDER BY createdAt DESC")
  fun observeAnnotations(bookId: String, includeHidden: Boolean): Flow<List<AnnotationEntity>>

  @Query("SELECT * FROM annotations WHERE bookId = :bookId AND (:includeHidden = 1 OR hidden = 0) ORDER BY createdAt DESC")
  suspend fun getAnnotations(bookId: String, includeHidden: Boolean): List<AnnotationEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAnnotation(annotation: AnnotationEntity): Long

  @Query("UPDATE annotations SET hidden = :hidden WHERE id = :annotationId")
  suspend fun setHidden(annotationId: Long, hidden: Boolean)

  @Query("DELETE FROM annotations WHERE id = :annotationId")
  suspend fun deleteById(annotationId: Long)

  @Query("DELETE FROM annotations WHERE bookId = :bookId")
  suspend fun deleteByBookId(bookId: String)

  @Query("SELECT * FROM annotations")
  suspend fun getAllAnnotations(): List<AnnotationEntity>
}