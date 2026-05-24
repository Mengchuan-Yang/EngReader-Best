package com.engreader.app.ai

interface FallbackPolicy {
  fun shouldFallback(error: TranslationError): Boolean
}

object NetworkTimeoutApiFallbackPolicy : FallbackPolicy {
  override fun shouldFallback(error: TranslationError): Boolean {
    return when (error) {
      is TranslationError.Network -> true
      is TranslationError.Timeout -> true
      is TranslationError.Api -> true
    }
  }
}
