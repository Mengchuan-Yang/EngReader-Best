package com.engreader.app.ai

interface WordTranslationService {
  suspend fun translateWord(request: WordTranslationRequest): TranslationResult<TranslationOutput>
}

interface SentenceTranslationService {
  suspend fun translateSentence(request: SentenceTranslationRequest): TranslationResult<TranslationOutput>
}
