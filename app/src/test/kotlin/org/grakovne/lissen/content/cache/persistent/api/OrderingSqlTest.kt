package org.grakovne.lissen.content.cache.persistent.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderingSqlTest {
  @Test
  fun `maps every library ordering option to its own column`() {
    assertEquals("title", resolveOrderField("title"))
    assertEquals("author", resolveOrderField("author"))
    assertEquals("createdAt", resolveOrderField("createdAt"))
    assertEquals("updatedAt", resolveOrderField("updatedAt"))
  }

  @Test
  fun `falls back to title for unknown fields`() {
    assertEquals("title", resolveOrderField("duration"))
    assertEquals("title", resolveOrderField("nonsense"))
  }

  @Test
  fun `normalizes direction and rejects garbage`() {
    assertEquals("ASC", resolveOrderDirection("asc"))
    assertEquals("DESC", resolveOrderDirection("desc"))
    assertEquals("ASC", resolveOrderDirection("sideways"))
  }
}
