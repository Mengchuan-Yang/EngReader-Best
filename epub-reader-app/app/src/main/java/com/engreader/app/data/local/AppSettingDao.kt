package com.engreader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {
  @Query("SELECT * FROM app_settings ORDER BY key ASC")
  fun observeAllSettings(): Flow<List<AppSettingEntity>>

  @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
  fun observeSetting(key: String): Flow<AppSettingEntity?>

  @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
  suspend fun getSetting(key: String): AppSettingEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSetting(setting: AppSettingEntity)

  @Query("DELETE FROM app_settings WHERE `key` = :key")
  suspend fun deleteSetting(key: String)

  @Query("SELECT * FROM app_settings")
  suspend fun getAllSettings(): List<AppSettingEntity>
}