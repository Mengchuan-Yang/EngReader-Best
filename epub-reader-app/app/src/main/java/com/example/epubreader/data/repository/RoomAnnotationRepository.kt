package com.engreader.app.data.repository

import com.engreader.app.data.local.AnnotationDao
import com.engreader.app.domain.model.Annotation
import com.engreader.app.domain.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAnnotationRepository(private val annotationDao: AnnotationDao) : AnnotationRepository {
  override fun observeAnnotations(bookId: String, includeHidden: Boolean): Flow<List<Annotation>> =
    annotationDao.observeAnnotations(bookId, includeHidden).map { entities -> entities.map { it.toDomain() } }

  override suspend fun getAnnotations(bookId: String, includeHidden: Boolean): List<Annotation> =
    annotationDao.getAnnotations(bookId, includeHidden).map { it.toDomain() }

  override suspend fun addAnnotation(annotation: Annotation): Long =
    annotationDao.insertAnnotation(annotation.toEntity())

  override suspend fun setHidden(annotationId: Long, hidden: Boolean) {
    annotationDao.setHidden(annotationId, hidden)
  }

  override suspend fun removeAnnotation(annotationId: Long) {
    annotationDao.deleteById(annotationId)
  }
}