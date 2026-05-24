package com.engreader.app.data.repository

import com.engreader.app.data.local.AnnotationEntity
import com.engreader.app.data.local.AnnotationTypeEntity
import com.engreader.app.data.local.AppSettingEntity
import com.engreader.app.data.local.BookEntity
import com.engreader.app.data.local.BookmarkEntity
import com.engreader.app.data.local.ReadingProgressEntity
import com.engreader.app.domain.model.Annotation
import com.engreader.app.domain.model.AnnotationType
import com.engreader.app.domain.model.AppSetting
import com.engreader.app.domain.model.Book
import com.engreader.app.domain.model.Bookmark
import com.engreader.app.domain.model.ReadingProgress

internal fun BookEntity.toDomain(): Book =
  Book(
    id = id,
    title = title,
    author = author,
    coverPath = coverPath,
    filePath = filePath,
    importedAt = importedAt,
    lastReadAt = lastReadAt
  )

internal fun Book.toEntity(): BookEntity =
  BookEntity(
    id = id,
    title = title,
    author = author,
    coverPath = coverPath,
    filePath = filePath,
    importedAt = importedAt,
    lastReadAt = lastReadAt
  )

internal fun AnnotationEntity.toDomain(): Annotation =
  Annotation(
    id = id,
    bookId = bookId,
    chapterRef = chapterRef,
    anchor = anchor,
    startOffset = startOffset,
    endOffset = endOffset,
    sourceText = sourceText,
    translation = translation,
    type = type.toDomain(),
    createdAt = createdAt,
    hidden = hidden
  )

internal fun Annotation.toEntity(): AnnotationEntity =
  AnnotationEntity(
    id = id,
    bookId = bookId,
    chapterRef = chapterRef,
    anchor = anchor,
    startOffset = startOffset,
    endOffset = endOffset,
    sourceText = sourceText,
    translation = translation,
    type = type.toEntity(),
    createdAt = createdAt,
    hidden = hidden
  )

internal fun AnnotationTypeEntity.toDomain(): AnnotationType =
  when (this) {
    AnnotationTypeEntity.WORD -> AnnotationType.WORD
    AnnotationTypeEntity.SENTENCE -> AnnotationType.SENTENCE
  }

internal fun AnnotationType.toEntity(): AnnotationTypeEntity =
  when (this) {
    AnnotationType.WORD -> AnnotationTypeEntity.WORD
    AnnotationType.SENTENCE -> AnnotationTypeEntity.SENTENCE
  }

internal fun BookmarkEntity.toDomain(): Bookmark =
  Bookmark(
    id = id,
    bookId = bookId,
    chapterRef = chapterRef,
    progress = progress,
    label = label,
    createdAt = createdAt
  )

internal fun Bookmark.toEntity(): BookmarkEntity =
  BookmarkEntity(
    id = id,
    bookId = bookId,
    chapterRef = chapterRef,
    progress = progress,
    label = label,
    createdAt = createdAt
  )

internal fun ReadingProgressEntity.toDomain(): ReadingProgress =
  ReadingProgress(
    bookId = bookId,
    chapterRef = chapterRef,
    progress = progress,
    lastPosition = lastPosition,
    lastUpdated = lastUpdated
  )

internal fun ReadingProgress.toEntity(): ReadingProgressEntity =
  ReadingProgressEntity(
    bookId = bookId,
    chapterRef = chapterRef,
    progress = progress,
    lastPosition = lastPosition,
    lastUpdated = lastUpdated
  )

internal fun AppSettingEntity.toDomain(): AppSetting = AppSetting(key = key, value = value)

internal fun AppSetting.toEntity(): AppSettingEntity = AppSettingEntity(key = key, value = value)