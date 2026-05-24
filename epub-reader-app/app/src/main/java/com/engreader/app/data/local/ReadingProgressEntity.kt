package com.engreader.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "reading_progress",
  primaryKeys = ["bookId", "chapterRef"],
  foreignKeys = [
    ForeignKey(
      entity = BookEntity::class,
      parentColumns = ["id"],
      childColumns = ["bookId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index("bookId"),
    Index("lastUpdated")
  ]
)
data class ReadingProgressEntity(
  val bookId: String,
  val chapterRef: String,
  val progress: Float,
  val lastPosition: String,
  val lastUpdated: Long
)