package com.engreader.app.domain.repository

import com.engreader.app.domain.model.Annotation
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
  fun observeAnnotations(bookId: String, includeHidden: Boolean = false): Flow<List<Annotation>>

  suspend fun getAnnotations(bookId: String, includeHidden: Boolean = false): List<Annotation>

  suspend fun addAnnotation(annotation: Annotation): Long

  suspend fun setHidden(annotationId: Long, hidden: Boolean)

  suspend fun removeAnnotation(annotationId: Long)
}