package com.engreader.app.domain.model

data class Bookmark(
  val id: Long = 0,
  val bookId: String,
  val chapterRef: String,
  val progress: Float,
  val label: String?,
  val createdAt: Long
)