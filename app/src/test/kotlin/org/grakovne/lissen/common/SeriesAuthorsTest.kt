package org.grakovne.lissen.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeriesAuthorsTest {
  @Test
  fun `empty input produces null`() {
    assertNull(mergeAuthorNames(emptyList()))
  }

  @Test
  fun `null and blank authors are ignored`() {
    assertNull(mergeAuthorNames(listOf(null, "", "   ")))
  }

  @Test
  fun `single author is returned as is`() {
    assertEquals("Frank Herbert", mergeAuthorNames(listOf("Frank Herbert")))
  }

  @Test
  fun `duplicate authors across books are removed`() {
    assertEquals("Frank Herbert", mergeAuthorNames(listOf("Frank Herbert", "Frank Herbert")))
  }

  @Test
  fun `multi-author values are split and deduplicated preserving order`() {
    assertEquals(
      "Frank Herbert, Brian Herbert, Kevin Anderson",
      mergeAuthorNames(listOf("Frank Herbert", "Frank Herbert, Brian Herbert", "Kevin Anderson")),
    )
  }

  @Test
  fun `surrounding whitespace is trimmed`() {
    assertEquals("A, B", mergeAuthorNames(listOf(" A , B ")))
  }
}
