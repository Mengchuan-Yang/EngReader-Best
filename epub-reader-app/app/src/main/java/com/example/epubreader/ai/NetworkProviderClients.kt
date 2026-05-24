package com.engreader.app.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val CONNECT_TIMEOUT_MS = 8000
private const val READ_TIMEOUT_MS = 15000

internal abstract class BaseHttpProviderClient(
  override val provider: AiProvider,
) : ProviderTranslationClient {

  override suspend fun translateWord(
    request: WordTranslationRequest,
    apiKey: String,
  ): TranslationResult<TranslationOutput> {
    return translateInternal(
      sourceText = request.word,
      contextSnippet = request.contextSnippet,
      isSentence = false,
      apiKey = apiKey,
    )
  }

  override suspend fun translateSentence(
    request: SentenceTranslationRequest,
    apiKey: String,
  ): TranslationResult<TranslationOutput> {
    return translateInternal(
      sourceText = request.sentence,
      contextSnippet = request.contextSnippet,
      isSentence = true,
      apiKey = apiKey,
    )
  }

  private suspend fun translateInternal(
    sourceText: String,
    contextSnippet: String,
    isSentence: Boolean,
    apiKey: String,
  ): TranslationResult<TranslationOutput> {
    if (apiKey.isBlank()) {
      return TranslationResult.Failure(TranslationError.Api(provider, "Provider API key is not configured"))
    }
    return withContext(Dispatchers.IO) {
      try {
        val translated = requestTranslation(sourceText.trim(), contextSnippet.trim(), isSentence, apiKey)
        if (translated.isBlank()) {
          TranslationResult.Failure(TranslationError.Api(provider, "Empty translation response"))
        } else {
          TranslationResult.Success(TranslationOutput(shortCn = translated, provider = provider))
        }
      } catch (e: SocketTimeoutException) {
        TranslationResult.Failure(TranslationError.Timeout(provider, e.message))
      } catch (e: IOException) {
        TranslationResult.Failure(TranslationError.Network(provider, e.message))
      } catch (e: Exception) {
        TranslationResult.Failure(TranslationError.Api(provider, e.message))
      }
    }
  }

  protected abstract fun requestTranslation(
    sourceText: String,
    contextSnippet: String,
    isSentence: Boolean,
    apiKey: String,
  ): String
}

internal class DeepSeekProviderClient : BaseHttpProviderClient(AiProvider.DEEPSEEK) {
  override fun requestTranslation(sourceText: String, contextSnippet: String, isSentence: Boolean, apiKey: String): String {
    val systemPrompt =
      if (isSentence) {
        "你是英文到简体中文翻译器。结合上下文纠正语义。输出仅一行简体中文，不要解释。"
      } else {
        "你是英文词汇到简体中文释义器。结合上下文挑选最准确词义。输出1-2个最准确中文义，不要解释。"
      }
    val userPrompt =
      if (isSentence) {
        "目标句子: $sourceText\n上下文: $contextSnippet"
      } else {
        "目标单词: $sourceText\n上下文: $contextSnippet"
      }
    return callOpenAiCompatible(
      url = "https://api.deepseek.com/chat/completions",
      apiKey = apiKey,
      model = "deepseek-chat",
      systemPrompt = systemPrompt,
      userPrompt = userPrompt,
    )
  }
}

internal class OpenAiProviderClient : BaseHttpProviderClient(AiProvider.OPENAI) {
  override fun requestTranslation(sourceText: String, contextSnippet: String, isSentence: Boolean, apiKey: String): String {
    val systemPrompt =
      if (isSentence) {
        "Translate English into concise Simplified Chinese with context disambiguation. Return Chinese only."
      } else {
        "Translate English word into 1-2 most accurate Simplified Chinese senses based on context. Return Chinese only."
      }
    val userPrompt =
      if (isSentence) {
        "Sentence: $sourceText\nContext: $contextSnippet"
      } else {
        "Word: $sourceText\nContext: $contextSnippet"
      }
    return callOpenAiCompatible(
      url = "https://api.openai.com/v1/chat/completions",
      apiKey = apiKey,
      model = "gpt-4o-mini",
      systemPrompt = systemPrompt,
      userPrompt = userPrompt,
    )
  }
}

internal class GeminiProviderClient : BaseHttpProviderClient(AiProvider.GEMINI) {
  override fun requestTranslation(sourceText: String, contextSnippet: String, isSentence: Boolean, apiKey: String): String {
    val prompt =
      if (isSentence) {
        "你是英文到简体中文翻译器。结合上下文纠正语义。仅输出一行中文。\n句子: $sourceText\n上下文: $contextSnippet"
      } else {
        "你是英文词汇释义器。结合上下文给出1-2个最准确简体中文义项。仅输出中文。\n单词: $sourceText\n上下文: $contextSnippet"
      }

    val body =
      JSONObject()
        .put(
          "contents",
          JSONArray().put(
            JSONObject().put(
              "parts",
              JSONArray().put(
                JSONObject().put("text", prompt),
              ),
            ),
          ),
        )
        .put(
          "generationConfig",
          JSONObject()
            .put("temperature", 0.2)
            .put("maxOutputTokens", 80),
        )

    val response =
      postJson(
        endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey",
        body = body,
        headers = emptyMap(),
      )

    val candidates = response.optJSONArray("candidates") ?: JSONArray()
    if (candidates.length() == 0) return ""
    val content = candidates.getJSONObject(0).optJSONObject("content") ?: JSONObject()
    val parts = content.optJSONArray("parts") ?: JSONArray()
    if (parts.length() == 0) return ""
    return parts.getJSONObject(0).optString("text").trim()
  }
}

private fun callOpenAiCompatible(
  url: String,
  apiKey: String,
  model: String,
  systemPrompt: String,
  userPrompt: String,
): String {
  val body =
    JSONObject()
      .put("model", model)
      .put(
        "messages",
        JSONArray()
          .put(JSONObject().put("role", "system").put("content", systemPrompt))
          .put(JSONObject().put("role", "user").put("content", userPrompt)),
      )
      .put("temperature", 0.2)
      .put("max_tokens", 80)

  val response =
    postJson(
      endpoint = url,
      body = body,
      headers = mapOf("Authorization" to "Bearer $apiKey"),
    )

  val choices = response.optJSONArray("choices") ?: JSONArray()
  if (choices.length() == 0) return ""
  val message = choices.getJSONObject(0).optJSONObject("message") ?: JSONObject()
  return message.optString("content").trim()
}

private fun postJson(
  endpoint: String,
  body: JSONObject,
  headers: Map<String, String>,
): JSONObject {
  val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
    requestMethod = "POST"
    connectTimeout = CONNECT_TIMEOUT_MS
    readTimeout = READ_TIMEOUT_MS
    doInput = true
    doOutput = true
    setRequestProperty("Content-Type", "application/json")
    headers.forEach { (k, v) -> setRequestProperty(k, v) }
  }

  try {
    connection.outputStream.use { stream ->
      stream.write(body.toString().toByteArray())
      stream.flush()
    }

    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() } ?: ""

    if (code !in 200..299) {
      throw IOException("HTTP $code: $text")
    }

    return JSONObject(text)
  } finally {
    connection.disconnect()
  }
}
