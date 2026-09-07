package org.grakovne.lissen.content.cache.persistent.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchRequestBuilderTest {
  private fun builder() = SearchRequestBuilder()

  @Test
  fun `query contains LIKE clauses for title author and seriesNames`() {
    val sql =
      builder()
        .libraryId("lib")
        .searchQuery("hobbit")
        .build()
        .sql
    val occurrences = "LIKE".toRegex().findAll(sql).count()
    assertEquals(3, occurrences, "Expected 3 LIKE clauses (title, author, seriesNames), got: $sql")
  }

  @Test
  fun `search query is wrapped in percent wildcards`() {
    val query = builder().libraryId("lib").searchQuery("hobbit").build()
    // The pattern %hobbit% should be bound as args (not inline in SQL)
    assertTrue(query.sql.contains("LIKE ?"), "Expected LIKE ? parameter, got: ${query.sql}")
    // 4 args: libraryId + 3 LIKE patterns
    assertEquals(4, query.argCount)
  }

  @Test
  fun `libraryId is included as a bound argument`() {
    val query = builder().libraryId("lib-42").searchQuery("test").build()
    assertEquals(4, query.argCount)
  }

  @Test
  fun `valid order field is used directly`() {
    val sql = builder().orderField("author").build().sql
    assertTrue(sql.contains("author"), "Expected author in ORDER BY, got: $sql")
  }

  @Test
  fun `unknown order field falls back to title`() {
    val sql = builder().orderField("xyz").build().sql
    assertTrue(sql.contains("title"), "Expected fallback to title, got: $sql")
  }

  @Test
  fun `ASC direction is preserved`() {
    val sql = builder().orderDirection("ASC").build().sql
    assertTrue(sql.contains("ASC"), "got: $sql")
  }

  @Test
  fun `lowercase asc is normalized`() {
    val sql = builder().orderDirection("asc").build().sql
    assertTrue(sql.contains("ASC"), "got: $sql")
  }

  @Test
  fun `invalid direction falls back to ASC`() {
    val sql = builder().orderDirection("RANDOM").build().sql
    assertTrue(sql.contains("ASC"), "got: $sql")
  }
}
