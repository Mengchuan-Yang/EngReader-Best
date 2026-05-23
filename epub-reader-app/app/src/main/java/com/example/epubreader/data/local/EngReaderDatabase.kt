package com.engreader.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities = [
    BookEntity::class,
    AnnotationEntity::class,
    BookmarkEntity::class,
    ReadingProgressEntity::class,
    AppSettingEntity::class
  ],
  version = 1,
  exportSchema = true
)
@TypeConverters(AnnotationTypeConverters::class)
abstract class EngReaderDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao
  abstract fun annotationDao(): AnnotationDao
  abstract fun bookmarkDao(): BookmarkDao
  abstract fun readingProgressDao(): ReadingProgressDao
  abstract fun appSettingDao(): AppSettingDao

  companion object {
    private const val DATABASE_NAME = "engreader.db"

    @Volatile private var instance: EngReaderDatabase? = null

    fun getInstance(context: Context): EngReaderDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
              context.applicationContext,
              EngReaderDatabase::class.java,
              DATABASE_NAME
            )
              .fallbackToDestructiveMigration()
              .build()
              .also { instance = it }
        }
    }
  }
}
