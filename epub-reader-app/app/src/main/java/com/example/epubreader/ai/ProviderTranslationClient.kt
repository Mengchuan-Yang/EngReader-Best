package com.engreader.app.ai

interface ProviderTranslationClient {
  val provider: AiProvider

  suspend fun translateWord(
    request: WordTranslationRequest,
    apiKey: String
  ): TranslationResult<TranslationOutput>

  suspend fun translateSentence(
    request: SentenceTranslationRequest,
    apiKey: String
  ): TranslationResult<TranslationOutput>
}
