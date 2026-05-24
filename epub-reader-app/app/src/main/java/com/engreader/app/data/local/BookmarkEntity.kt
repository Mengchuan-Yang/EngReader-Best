package com.engreader.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "bookmarks",
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
    Index("createdAt")
  ]
)
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val bookId: String,
  val chapterRef: String,
  val progress: Float,
  val label: String?,
  val createdAt: Long
)