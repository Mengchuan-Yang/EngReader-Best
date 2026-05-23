package com.engreader.app.domain.model

data class Book(
  val id: String,
  val title: String,
  val author: String,
  val coverPath: String?,
  val filePath: String,
  val importedAt: Long,
  val lastReadAt: Long?
)