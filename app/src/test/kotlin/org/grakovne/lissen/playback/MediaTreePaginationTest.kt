package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaTreePaginationTest {
  private val items = (0..9).toList()

  @Test
  fun `returns requested page`() {
    assertEquals(listOf(3, 4, 5), items.page(page = 1, pageSize = 3))
  }

  @Test
  fun `returns remaining items on last page`() {
    assertEquals(listOf(8, 9), items.page(page = 2, pageSize = 4))
  }

  @Test
  fun `returns empty page for invalid arguments and offsets`() {
    assertEquals(emptyList<Int>(), items.page(page = -1, pageSize = 3))
    assertEquals(emptyList<Int>(), items.page(page = 0, pageSize = 0))
    assertEquals(emptyList<Int>(), items.page(page = 5, pageSize = 3))
    assertEquals(emptyList<Int>(), items.page(page = Int.MAX_VALUE, pageSize = Int.MAX_VALUE))
  }
}
