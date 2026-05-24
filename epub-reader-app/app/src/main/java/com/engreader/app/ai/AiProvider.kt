package com.engreader.app.ai

enum class AiProvider {
  DEEPSEEK,
  GEMINI,
  OPENAI;

  val displayName: String
    get() = when (this) {
      DEEPSEEK -> "DeepSeek"
      GEMINI -> "Gemini"
      OPENAI -> "OpenAI"
    }
}
