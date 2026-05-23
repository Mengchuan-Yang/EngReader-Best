package com.engreader.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "annotations",
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
data class AnnotationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val bookId: String,
  val chapterRef: String,
  val anchor: String,
  val startOffset: Int,
  val endOffset: Int,
  val sourceText: String,
  val translation: String?,
  val type: AnnotationTypeEntity,
  val createdAt: Long,
  val hidden: Boolean
)