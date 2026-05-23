package com.engreader.app.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.engreader.app.ai.AiProvider

class SecureApiKeyStore(context: Context) {
  private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

  private val prefs =
    EncryptedSharedPreferences.create(
      context,
      PREFS_FILE,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

  fun putApiKey(provider: AiProvider, apiKey: String) {
    prefs.edit().putString(provider.name, apiKey).apply()
  }

  fun getApiKey(provider: AiProvider): String? = prefs.getString(provider.name, null)

  fun loadSettings(): AiProviderSettings {
    val map =
      AiProvider.entries.associateWith { provider ->
        val key = getApiKey(provider)
        if (key.isNullOrBlank()) null else ApiKeyConfig(apiKey = key)
      }
        .filterValues { it != null }
        .mapValues { it.value!! }
    return AiProviderSettings(providerConfigs = map)
  }

  companion object {
    private const val PREFS_FILE = "engreader_secure_keys"
  }
}
