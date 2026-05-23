package com.engreader.app.data.repository

import com.engreader.app.data.local.AppSettingDao
import com.engreader.app.domain.model.AppSetting
import com.engreader.app.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAppSettingsRepository(private val appSettingDao: AppSettingDao) : AppSettingsRepository {
  override fun observeAllSettings(): Flow<List<AppSetting>> =
    appSettingDao.observeAllSettings().map { entities -> entities.map { it.toDomain() } }

  override fun observeSetting(key: String): Flow<AppSetting?> =
    appSettingDao.observeSetting(key).map { it?.toDomain() }

  override suspend fun getSetting(key: String): AppSetting? = appSettingDao.getSetting(key)?.toDomain()

  override suspend fun upsertSetting(setting: AppSetting) {
    appSettingDao.upsertSetting(setting.toEntity())
  }

  override suspend fun removeSetting(key: String) {
    appSettingDao.deleteSetting(key)
  }
}