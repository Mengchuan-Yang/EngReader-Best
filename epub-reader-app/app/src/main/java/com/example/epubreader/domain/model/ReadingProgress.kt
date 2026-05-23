package com.engreader.app.domain.model

data class ReadingProgress(
  val bookId: String,
  val chapterRef: String,
  val progress: Float,
  val lastPosition: String,
  val lastUpdated: Long
)