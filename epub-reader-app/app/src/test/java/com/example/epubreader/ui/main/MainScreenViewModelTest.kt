package com.engreader.app.ui.main

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class MainScreenViewModelTest {

  @Test
  fun extractWordAtOffset_simpleWord() {
    val text = "The quick brown fox"
    // 'quick' starts at offset 4
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

  companion object {
    // Replicating the private logic from MainScreenViewModel for unit testing
    private fun extractWordAtOffsetForTest(text: String, offset: Int): String? {
      if (text.isEmpty() || offset !in text.indices) return null
      if (!text[offset].isLetter()) return null
      var start = offset
      var end = offset
      while (start > 0 && text[start - 1].isLetter()) start--
      while (end < text.lastIndex && text[end + 1].isLetter()) end++
      return text.substring(start, end + 1).trim().takeIf { it.isNotBlank() }
    }
  }
}
