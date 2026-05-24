package com.engreader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingEntity(
  @PrimaryKey val key: String,
  val value: String
)