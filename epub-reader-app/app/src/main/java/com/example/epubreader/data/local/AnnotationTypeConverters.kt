package com.engreader.app.data.local

import androidx.room.TypeConverter

class AnnotationTypeConverters {
  @TypeConverter
  fun toStoredValue(value: AnnotationTypeEntity): String = value.name

  @TypeConverter
  fun fromStoredValue(value: String): AnnotationTypeEntity = AnnotationTypeEntity.valueOf(value)
}