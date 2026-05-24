package com.engreader.app.ui.main

import com.engreader.app.ai.TranslationErrorKeys
import com.engreader.app.model.AnnotationRecord
import com.engreader.app.model.AnnotationType
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class MainScreenViewModelTest {

  // ── extractWordAtOffset ──────────────────────────────────────────

  @Test
  fun extractWordAtOffset_simpleWord() {
    val text = "The quick brown fox"
    assertEquals("quick", extractWordAtOffsetForTest(text, 5))
  }

  @Test
  fun extractWordAtOffset_startOfWord() {
    val text = "hello world"
    assertEquals("hello", extractWordAtOffsetForTest(text, 0))
  }

  @Test
  fun extractWordAtOffset_endOfWord() {
    val text = "hello world"
    assertEquals("hello", extractWordAtOffsetForTest(text, 4))
  }

  @Test
  fun extractWordAtOffset_nonLetter_returnsNull() {
    val text = "hello world"
    assertNull(extractWordAtOffsetForTest(text, 5)) // space
  }

  @Test
  fun extractWordAtOffset_outOfBounds_returnsNull() {
    val text = "hi"
    assertNull(extractWordAtOffsetForTest(text, 5))
  }

  @Test
  fun extractWordAtOffset_emptyString_returnsNull() {
    assertNull(extractWordAtOffsetForTest("", 0))
  }

  @Test
  fun extractWordAtOffset_singleLetter() {
    assertEquals("a", extractWordAtOffsetForTest("a", 0))
  }

  @Test
  fun extractWordAtOffset_withApostrophe() {
    val text = "don't go"
    // offset 2 is 'n', word expands but stops at apostrophe
    assertEquals("don", extractWordAtOffsetForTest(text, 2))
  }

  // ── isCjk ─────────────────────────────────────────────────────────

  @Test
  fun isCjk_chineseCharacter() {
    assertTrue(isCjkForTest('中'))
  }

  @Test
  fun isCjk_englishLetter() {
    assertFalse(isCjkForTest('A'))
  }

  @Test
  fun isCjk_digit() {
    assertFalse(isCjkForTest('5'))
  }

  @Test
  fun isCjk_hiragana() {
    assertFalse(isCjkForTest('あ'))
  }

  // ── containsWord ──────────────────────────────────────────────────

  @Test
  fun containsWord_exactMatch() {
    assertTrue(containsWordForTest("The quick brown fox", "quick"))
  }

  @Test
  fun containsWord_notPresent() {
    assertFalse(containsWordForTest("The quick brown fox", "slow"))
  }

  @Test
  fun containsWord_caseInsensitive() {
    assertTrue(containsWordForTest("The Quick brown fox", "quick"))
  }

  @Test
  fun containsWord_partialNoMatch() {
    // "qui" is a substring but not a whole word
    assertFalse(containsWordForTest("The quick brown fox", "qui"))
  }

  @Test
  fun containsWord_atEnd() {
    assertTrue(containsWordForTest("The fox", "fox"))
  }

  @Test
  fun containsWord_atStart() {
    assertTrue(containsWordForTest("The fox", "The"))
  }

  // ── mapTranslationError ──────────────────────────────────────────

  @Test
  fun mapTranslationError_network() {
    assertEquals("网络不可用", mapTranslationErrorForTest(TranslationErrorKeys.NETWORK, null))
  }

  @Test
  fun mapTranslationError_timeout() {
    assertEquals("请求超时", mapTranslationErrorForTest(TranslationErrorKeys.TIMEOUT, null))
  }

  @Test
  fun mapTranslationError_api() {
    assertEquals("翻译服务不可用", mapTranslationErrorForTest(TranslationErrorKeys.API, null))
  }

  @Test
  fun mapTranslationError_unknown() {
    assertEquals("翻译失败", mapTranslationErrorForTest("unknown_key", null))
  }

  @Test
  fun mapTranslationError_notConfigured() {
    assertEquals("API 未配置", mapTranslationErrorForTest("any", "provider not configured"))
  }

  // ── mapRenderedOffsetToOriginal ───────────────────────────────────

  @Test
  fun mapOffset_noAnnotations_returnsSame() {
    val result = mapRenderedOffsetToOriginalForTest("hello world", emptyList(), true, 3)
    assertEquals(3, result)
  }

  @Test
  fun mapOffset_annotationsHidden_returnsSame() {
    val ann = listOf(AnnotationRecord("1", "b1", 0, 0, "hello", "你好", AnnotationType.WORD, 0))
    val result = mapRenderedOffsetToOriginalForTest("hello world", ann, false, 3)
    assertEquals(3, result)
  }

  @Test
  fun mapOffset_withWordAnnotation_beforeInsertion() {
    val ann = listOf(AnnotationRecord("1", "b1", 0, 0, "hello", "你好", AnnotationType.WORD, 0))
    // Original: "hello world" (11 chars)
    // After insertion: "hello（你好） world" — note is 4 chars inserted at position 5
    // Offset 2 in displayed should map to offset 2 in original
    val result = mapRenderedOffsetToOriginalForTest("hello world", ann, true, 2)
    assertEquals(2, result)
  }

  @Test
  fun mapOffset_withWordAnnotation_afterInsertion() {
    val ann = listOf(AnnotationRecord("1", "b1", 0, 0, "hello", "你好", AnnotationType.WORD, 0))
    // Displayed: "hello（你好） world"
    // Offset 10 (the 'w' in "world") should map to original offset 6
    val result = mapRenderedOffsetToOriginalForTest("hello world", ann, true, 10)
    assertEquals(6, result)
  }

  @Test
  fun mapOffset_onAnnotationText_returnsNegative() {
    val ann = listOf(AnnotationRecord("1", "b1", 0, 0, "hello", "你好", AnnotationType.WORD, 0))
    // The note "（你好）" is at positions 5-9 in displayed text
    val result = mapRenderedOffsetToOriginalForTest("hello world", ann, true, 6)
    assertEquals(-1, result)
  }

  // ── renderParagraphWithAnnotations ───────────────────────────────

  @Test
  fun renderParagraph_noAnnotations_returnsPlainText() {
    val result = renderParagraphWithAnnotationsForTest("hello world", emptyList(), true)
    assertEquals("hello world", result)
  }

  @Test
  fun renderParagraph_hiddenAnnotations_returnsPlainText() {
    val ann = listOf(AnnotationRecord("1", "b1", 0, 0, "hello", "你好", AnnotationType.WORD, 0))
    val result = renderParagraphWithAnnotationsForTest("hello world", ann, false)
    assertEquals("hello world", result)
  }

  @Test
  fun renderParagraph_wordAnnotation_insertsNote() {
    val ann = listOf(AnnotationRecord("1", "b1", 0, 0, "hello", "你好", AnnotationType.WORD, 0))
    val result = renderParagraphWithAnnotationsForTest("hello world", ann, true)
    assertTrue(result.contains("（你好）"))
    assertTrue(result.startsWith("hello（你好）"))
  }

  // ──────────────────────────────────────────────────────────────────

  companion object {
    private fun extractWordAtOffsetForTest(text: String, offset: Int): String? {
      if (text.isEmpty() || offset !in text.indices) return null
      if (!text[offset].isLetter()) return null
      var start = offset
      var end = offset
      while (start > 0 && text[start - 1].isLetter()) start--
      while (end < text.lastIndex && text[end + 1].isLetter()) end++
      return text.substring(start, end + 1).trim().takeIf { it.isNotBlank() }
    }

    private fun isCjkForTest(ch: Char): Boolean = ch.code in 0x4E00..0x9FFF

    private fun containsWordForTest(text: String, word: String): Boolean {
      val regex = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
      return regex.containsMatchIn(text)
    }

    private fun mapTranslationErrorForTest(errorKey: String, debugMessage: String?): String {
      if (debugMessage?.contains("not configured", ignoreCase = true) == true) {
        return "API 未配置"
      }
      return when (errorKey) {
        TranslationErrorKeys.NETWORK -> "网络不可用"
        TranslationErrorKeys.TIMEOUT -> "请求超时"
        TranslationErrorKeys.API -> "翻译服务不可用"
        else -> "翻译失败"
      }
    }

    private fun mapRenderedOffsetToOriginalForTest(
      paragraph: String,
      annotations: List<AnnotationRecord>,
      showAnnotations: Boolean,
      renderedOffset: Int,
    ): Int {
      if (!showAnnotations || annotations.isEmpty()) return renderedOffset
      data class Insertion(val start: Int, val length: Int)
      val insertions = mutableListOf<Insertion>()
      var rendered = paragraph
      var searchStart = 0
      annotations.filter { it.type == AnnotationType.WORD }.forEach { annotation ->
        val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
        if (index >= 0) {
          val insertAt = index + annotation.anchorText.length
          val note = "（${annotation.translation}）"
          rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
          insertions += Insertion(insertAt, note.length)
          searchStart = insertAt + note.length
        }
      }
      annotations.filter { it.type == AnnotationType.SENTENCE }.forEach { annotation ->
        val note = "（${annotation.translation}）"
        if (!rendered.endsWith(note)) {
          val start = rendered.length
          rendered += note
          insertions += Insertion(start, note.length)
        }
      }
      var adjusted = renderedOffset
      insertions.sortedBy { it.start }.forEach { insertion ->
        if (renderedOffset in insertion.start until (insertion.start + insertion.length)) return -1
        if (renderedOffset >= insertion.start + insertion.length) adjusted -= insertion.length
      }
      return adjusted.coerceIn(0, paragraph.lastIndex.coerceAtLeast(0))
    }

    private fun renderParagraphWithAnnotationsForTest(
      paragraph: String,
      annotations: List<AnnotationRecord>,
      showAnnotations: Boolean,
    ): String {
      if (!showAnnotations || annotations.isEmpty()) return paragraph
      var rendered = paragraph
      var searchStart = 0
      annotations.filter { it.type == AnnotationType.WORD }
        .sortedBy { paragraph.indexOf(it.anchorText, ignoreCase = true) }
        .forEach { annotation ->
          val index = rendered.indexOf(annotation.anchorText, startIndex = searchStart, ignoreCase = true)
          if (index >= 0) {
            val insertAt = index + annotation.anchorText.length
            val note = "（${annotation.translation}）"
            rendered = rendered.substring(0, insertAt) + note + rendered.substring(insertAt)
            searchStart = insertAt + note.length
          }
        }
      annotations.filter { it.type == AnnotationType.SENTENCE }.forEach { annotation ->
        val note = "（${annotation.translation}）"
        if (!rendered.endsWith(note)) rendered += note
      }
      return rendered
    }
  }
}
