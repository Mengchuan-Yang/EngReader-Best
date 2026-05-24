package com.engreader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
  @PrimaryKey val id: String,
  val title: String,
  val author: String,
  val coverPath: String?,
  val filePath: String,
  val importedAt: Long,
  val lastReadAt: Long?
)