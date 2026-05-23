package com.engreader.app.domain.repository

import com.engreader.app.domain.model.AppSetting
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
  fun observeAllSettings(): Flow<List<AppSetting>>

  fun observeSetting(key: String): Flow<AppSetting?>

  suspend fun getSetting(key: String): AppSetting?

  suspend fun upsertSetting(setting: AppSetting)

  suspend fun removeSetting(key: String)
}