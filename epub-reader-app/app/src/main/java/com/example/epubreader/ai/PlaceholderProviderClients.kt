package com.engreader.app.ai

internal abstract class BasePlaceholderProviderClient(
  override val provider: AiProvider
) : ProviderTranslationClient {

  override suspend fun translateWord(
    request: WordTranslationRequest,
    apiKey: String
  ): TranslationResult<TranslationOutput> {
    if (apiKey.isBlank()) {
      return TranslationResult.Failure(
        TranslationError.Api(provider, "Missing API key")
      )
    }

    val simulatedError = simulatedErrorFor(request.contextSnippet)
    if (simulatedError != null) {
      return TranslationResult.Failure(simulatedError)
    }

    val shortCn = buildString {
      append("词义：")
      append(request.word.trim())
    }
    return TranslationResult.Success(TranslationOutput(shortCn = shortCn, provider = provider))
  }

  override suspend fun translateSentence(
    request: SentenceTranslationRequest,
    apiKey: String
  ): TranslationResult<TranslationOutput> {
    if (apiKey.isBlank()) {
      return TranslationResult.Failure(
        TranslationError.Api(provider, "Missing API key")
      )
    }

    val simulatedError = simulatedErrorFor(request.contextSnippet)
    if (simulatedError != null) {
      return TranslationResult.Failure(simulatedError)
    }

    val normalized = request.sentence.trim().replace('\n', ' ')
    val brief = if (normalized.length <= 16) normalized else normalized.take(16) + "..."
    val shortCn = "句意：$brief"
    return TranslationResult.Success(TranslationOutput(shortCn = shortCn, provider = provider))
  }

  private fun simulatedErrorFor(contextSnippet: String): TranslationError? {
    return when {
      contextSnippet.contains("[network]", ignoreCase = true) ->
        TranslationError.Network(provider, "Simulated network failure")
      contextSnippet.contains("[timeout]", ignoreCase = true) ->
        TranslationError.Timeout(provider, "Simulated timeout failure")
      contextSnippet.contains("[api]", ignoreCase = true) ->
        TranslationError.Api(provider, "Simulated API failure")
      else -> null
    }
  }
}

internal class DeepSeekPlaceholderClient : BasePlaceholderProviderClient(AiProvider.DEEPSEEK)

internal class GeminiPlaceholderClient : BasePlaceholderProviderClient(AiProvider.GEMINI)

internal class OpenAiPlaceholderClient : BasePlaceholderProviderClient(AiProvider.OPENAI)
