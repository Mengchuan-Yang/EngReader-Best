package com.engreader.app.ai

data class WordTranslationRequest(
  val word: String,
  val contextSnippet: String
)

data class SentenceTranslationRequest(
  val sentence: String,
  val contextSnippet: String
)

data class TranslationOutput(
  val shortCn: String,
  val provider: AiProvider
)

sealed interface TranslationResult<out T> {
  data class Success<T>(val data: T) : TranslationResult<T>
  data class Failure(val error: TranslationError) : TranslationResult<Nothing>
}

object TranslationErrorKeys {
  const val NETWORK = "err_network"
  const val TIMEOUT = "err_timeout"
  const val API = "err_api"
}

sealed interface TranslationError {
  val provider: AiProvider
  val userErrorKey: String
  val debugMessage: String?

  data class Network(
    override val provider: AiProvider,
    override val debugMessage: String? = null
  ) : TranslationError {
    override val userErrorKey: String = TranslationErrorKeys.NETWORK
  }

  data class Timeout(
    override val provider: AiProvider,
    override val debugMessage: String? = null
  ) : TranslationError {
    override val userErrorKey: String = TranslationErrorKeys.TIMEOUT
  }

  data class Api(
    override val provider: AiProvider,
    override val debugMessage: String? = null
  ) : TranslationError {
    override val userErrorKey: String = TranslationErrorKeys.API
  }
}
