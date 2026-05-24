package com.engreader.app.domain.model

data class Annotation(
  val id: Long = 0,
  val bookId: String,
  val chapterRef: String,
  val anchor: String,
  val startOffset: Int,
  val endOffset: Int,
  val sourceText: String,
  val translation: String?,
  val type: AnnotationType,
  val createdAt: Long,
  val hidden: Boolean
)