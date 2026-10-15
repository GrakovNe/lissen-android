package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SupportSQLiteProgram
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.LibraryType
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
  inner class ExtraFilter {
    @Test
    fun `authors filter adds a like clause on authorsJson with the id argument`() {
      val query = builder().libraryId("lib-1").extraFilter("authors" to "auth-1").build()

      assertTrue(query.sql.contains("authorsJson LIKE ?"), "Expected authorsJson LIKE clause, got: ${query.sql}")

      val program = mockk<SupportSQLiteProgram>(relaxed = true)
      query.bindTo(program)
      verify { program.bindString(2, "%\"id\":\"auth-1\"%") }
    }

    @Test
    fun `not started filter excludes books with progress beyond zero`() {
      val query = builder().libraryId("lib-1").extraFilter("progress" to "not-started").build()

      assertTrue(query.sql.contains("NOT EXISTS"), "Expected NOT EXISTS clause, got: ${query.sql}")
      assertTrue(query.sql.contains("mp2.currentTime > 0"), "Expected currentTime guard, got: ${query.sql}")
    }

    @Test
    fun `unknown filter is ignored`() {
      val query = builder().libraryId("lib-1").extraFilter("mystery" to "value").build()

      assertFalse(query.sql.contains("mystery"), "Expected no clause, got: ${query.sql}")
    }

    @Test
    fun `count query applies the same extra filter`() {
      val query = builder().libraryId("lib-1").extraFilter("authors" to "auth-1").buildCount()

      assertTrue(query.sql.contains("authorsJson LIKE ?"), "Expected authorsJson LIKE clause, got: ${query.sql}")
      assertTrue(query.sql.contains("COUNT"), "Expected COUNT query, got: ${query.sql}")
    }

    @Test
    fun `extra filter combines with hide completed without join conflicts`() {
      val query =
        builder()
          .libraryId("lib-1")
          .libraryType(LibraryType.LIBRARY)
          .hideCompleted(true)
          .extraFilter("progress" to "not-started")
          .build()

      assertTrue(query.sql.contains("LEFT JOIN media_progress mp"), "Expected hideCompleted join, got: ${query.sql}")
      assertTrue(query.sql.contains("mp2"), "Expected independent progress subquery, got: ${query.sql}")
    }

    @Test
    fun `authors filter adds exactly one extra argument`() {
      val withFilter = builder().libraryId("lib-1").extraFilter("authors" to "auth-1").build()
      val withoutFilter = builder().libraryId("lib-1").build()

      assertEquals(withoutFilter.argCount + 1, withFilter.argCount)
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
