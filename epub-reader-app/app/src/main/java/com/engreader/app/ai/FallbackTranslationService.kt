package com.engreader.app.ai

import com.engreader.app.settings.AiProviderSettings

class FallbackTranslationService(
  private val settings: AiProviderSettings,
  private val priorityStrategy: AiProviderPriorityStrategy = DefaultAiProviderPriorityStrategy,
  private val fallbackPolicy: FallbackPolicy = NetworkTimeoutApiFallbackPolicy,
  providerClients: List<ProviderTranslationClient> = defaultProviderClients()
) : WordTranslationService, SentenceTranslationService {

  private val clientsByProvider: Map<AiProvider, ProviderTranslationClient> =
    providerClients.associateBy { it.provider }

  override suspend fun translateWord(
    request: WordTranslationRequest
  ): TranslationResult<TranslationOutput> {
    return runWithFallback { provider, apiKey ->
      val client = clientsByProvider[provider]
        ?: return@runWithFallback TranslationResult.Failure(
          TranslationError.Api(provider, "Provider client not registered")
        )
      client.translateWord(request, apiKey)
    }
  }

  override suspend fun translateSentence(
    request: SentenceTranslationRequest
  ): TranslationResult<TranslationOutput> {
    return runWithFallback { provider, apiKey ->
      val client = clientsByProvider[provider]
        ?: return@runWithFallback TranslationResult.Failure(
          TranslationError.Api(provider, "Provider client not registered")
        )
      client.translateSentence(request, apiKey)
    }
  }

  private suspend fun runWithFallback(
    translate: suspend (provider: AiProvider, apiKey: String) -> TranslationResult<TranslationOutput>
  ): TranslationResult<TranslationOutput> {
    val providerOrder = priorityStrategy.orderedProviders()
    var lastFailure: TranslationResult.Failure? = null

    for (provider in providerOrder) {
      val apiKey = settings.apiKeyFor(provider)
      if (apiKey.isNullOrBlank()) {
        lastFailure = TranslationResult.Failure(
          TranslationError.Api(provider, "Provider API key is not configured")
        )
        continue
      }

      val result = translate(provider, apiKey)
      when (result) {
        is TranslationResult.Success -> return result
        is TranslationResult.Failure -> {
          lastFailure = result
          if (!fallbackPolicy.shouldFallback(result.error)) {
            return result
          }
        }
      }
    }

    return lastFailure ?: TranslationResult.Failure(
      TranslationError.Api(
        provider = providerOrder.firstOrNull() ?: AiProvider.DEEPSEEK,
        debugMessage = "No providers available for fallback chain"
      )
    )
  }

  companion object {
    fun defaultProviderClients(): List<ProviderTranslationClient> = listOf(
      DeepSeekProviderClient(),
      GeminiProviderClient(),
      OpenAiProviderClient(),
    )
  }
}
