package com.engreader.app.domain.repository

import com.engreader.app.domain.model.EngReaderBackup

interface BackupRepository {
  suspend fun exportBackup(): EngReaderBackup

  suspend fun importBackup(backup: EngReaderBackup)
}