package com.engreader.app.settings

import com.engreader.app.ai.AiProvider

data class ApiKeyConfig(
  val apiKey: String
)

data class AiProviderSettings(
  val providerConfigs: Map<AiProvider, ApiKeyConfig> = emptyMap()
) {
  fun apiKeyFor(provider: AiProvider): String? {
    val key = providerConfigs[provider]?.apiKey ?: return null
    return key.takeIf { it.isNotBlank() }
  }
}
