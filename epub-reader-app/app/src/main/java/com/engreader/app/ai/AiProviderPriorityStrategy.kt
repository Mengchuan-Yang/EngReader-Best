package com.engreader.app.ai

interface AiProviderPriorityStrategy {
  fun orderedProviders(): List<AiProvider>
}

object DefaultAiProviderPriorityStrategy : AiProviderPriorityStrategy {
  override fun orderedProviders(): List<AiProvider> =
    listOf(AiProvider.DEEPSEEK, AiProvider.GEMINI, AiProvider.OPENAI)
}
