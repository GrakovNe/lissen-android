package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.lib.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FetchRequestBuilderTest {
  private fun builder() = FetchRequestBuilder()

  @Nested
  inner class LibraryClause {
    @Test
    fun `null libraryId generates IS NULL clause`() {
      val query = builder().libraryId(null).build()
      assertTrue(query.sql.contains("libraryId IS NULL"), "Expected IS NULL clause, got: ${query.sql}")
    }

    @Test
    fun `set libraryId generates equality clause`() {
      val query = builder().libraryId("lib-1").build()
      assertTrue(query.sql.contains("libraryId = ?"), "Expected equality clause, got: ${query.sql}")
    }
  }

  @Nested
  inner class HideCompleted {
    @Test
    fun `hideCompleted true with LIBRARY type adds isFinished filter`() {
      val query = builder().hideCompleted(true).libraryType(LibraryType.LIBRARY).build()
      assertTrue(query.sql.contains("isFinished"), "Expected isFinished clause, got: ${query.sql}")
    }

    @Test
    fun `hideCompleted true with PODCAST type does not add isFinished filter`() {
      val query = builder().hideCompleted(true).libraryType(LibraryType.PODCAST).build()
      assertFalse(query.sql.contains("isFinished"), "Expected no isFinished clause, got: ${query.sql}")
    }

    @Test
    fun `hideCompleted false with LIBRARY type does not add isFinished filter`() {
      val query = builder().hideCompleted(false).libraryType(LibraryType.LIBRARY).build()
      assertFalse(query.sql.contains("isFinished"), "Expected no isFinished clause, got: ${query.sql}")
    }
  }

  @Nested
  inner class OrderField {
    @Test
    fun `title order maps to b_title column`() {
      val query = builder().orderField("title").build()
      assertTrue(query.sql.contains("b.title"), "Expected b.title, got: ${query.sql}")
    }

    @Test
    fun `author order maps to b_author column`() {
      val query = builder().orderField("author").build()
      assertTrue(query.sql.contains("b.author"), "Expected b.author, got: ${query.sql}")
    }

    @Test
    fun `unknown order field falls back to b_title`() {
      val query = builder().orderField("unknown_field").build()
      assertTrue(query.sql.contains("b.title"), "Expected fallback to b.title, got: ${query.sql}")
    }
  }

  @Nested
  inner class OrderDirection {
    @Test
    fun `ASC direction is preserved`() {
      val query = builder().orderDirection("ASC").build()
      assertTrue(query.sql.contains("ASC"), "Expected ASC, got: ${query.sql}")
    }

    @Test
    fun `DESC direction is preserved`() {
      val query = builder().orderDirection("DESC").build()
      assertTrue(query.sql.contains("DESC"), "Expected DESC, got: ${query.sql}")
    }

    @Test
    fun `lowercase asc is normalized to ASC`() {
      val query = builder().orderDirection("asc").build()
      assertTrue(query.sql.contains("ASC"), "Expected normalized ASC, got: ${query.sql}")
    }

    @Test
    fun `invalid direction falls back to ASC`() {
      val query = builder().orderDirection("RANDOM").build()
      assertTrue(query.sql.contains("ASC"), "Expected fallback ASC, got: ${query.sql}")
    }
  }

  @Nested
  inner class Pagination {
    @Test
    fun `query contains LIMIT and OFFSET`() {
      val query = builder().pageSize(20).pageNumber(2).build()
      assertTrue(query.sql.contains("LIMIT"), "Expected LIMIT, got: ${query.sql}")
      assertTrue(query.sql.contains("OFFSET"), "Expected OFFSET, got: ${query.sql}")
    }

    @Test
    fun `arg count includes pageSize and offset`() {
      val query =
        builder()
          .libraryId("lib-1")
          .pageSize(10)
          .pageNumber(3)
          .build()
      // args: libraryId, pageSize, offset
      assertEquals(3, query.argCount)
    }

    @Test
    fun `null libraryId does not add libraryId arg`() {
      val query =
        builder()
          .libraryId(null)
          .pageSize(10)
          .pageNumber(0)
          .build()
      // args: only pageSize and offset (no libraryId for IS NULL)
      assertEquals(2, query.argCount)
    }
  }
}
