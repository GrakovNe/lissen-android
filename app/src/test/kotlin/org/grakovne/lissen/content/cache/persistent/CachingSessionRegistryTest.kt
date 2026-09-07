package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CachingSessionRegistryTest {
  private lateinit var registry: CachingSessionRegistry

  @BeforeEach
  fun setup() {
    registry = CachingSessionRegistry()
  }

  @Nested
  inner class Register {
    @Test
    fun `register marks item as in progress`() {
      registry.register("book-1", Job())

      assertTrue(registry.inProgress())
    }

    @Test
    fun `register replaces and cancels previous job for the same item`() {
      val first = Job()
      val second = Job()

      registry.register("book-1", first)
      registry.register("book-1", second)

      assertTrue(first.isCancelled)
      assertFalse(second.isCancelled)
    }
  }

  @Nested
  inner class Settle {
    @Test
    fun `settle with registered job clears in progress`() {
      val job = Job()
      registry.register("book-1", job)

      registry.settle("book-1", job)

      assertFalse(registry.inProgress())
    }

    @Test
    fun `settle with stale job keeps the current session`() {
      val stale = Job()
      val current = Job()
      registry.register("book-1", stale)
      registry.register("book-1", current)

      registry.settle("book-1", stale)

      assertTrue(registry.inProgress())
    }
  }

  @Nested
  inner class Cancel {
    @Test
    fun `cancel cancels the job and clears the status`() {
      val job = Job()
      registry.register("book-1", job)
      registry.updateStatus(detailedItem("book-1"), CacheState(CacheStatus.Caching))

      val cancelled = registry.cancel("book-1")

      assertTrue(cancelled)
      assertTrue(job.isCancelled)
      assertFalse(registry.inProgress())
      assertTrue(registry.notificationItems().isEmpty())
    }

    @Test
    fun `cancel of unknown item returns false`() {
      assertFalse(registry.cancel("unknown"))
    }
  }

  @Nested
  inner class Statuses {
    @Test
    fun `caching status keeps session in progress`() {
      val job = Job()
      registry.register("book-1", job)
      registry.updateStatus(detailedItem("book-1"), CacheState(CacheStatus.Caching))
      registry.settle("book-1", job)

      assertTrue(registry.inProgress())
    }

    @Test
    fun `completed status finishes the session`() {
      val job = Job()
      registry.register("book-1", job)
      registry.updateStatus(detailedItem("book-1"), CacheState(CacheStatus.Completed))
      registry.settle("book-1", job)

      assertFalse(registry.inProgress())
      assertFalse(registry.hasErrors())
    }

    @Test
    fun `error status is reported`() {
      registry.updateStatus(detailedItem("book-1"), CacheState(CacheStatus.Error))

      assertTrue(registry.hasErrors())
    }

    @Test
    fun `marked error is reported without statuses`() {
      registry.markError()

      assertTrue(registry.hasErrors())
    }
  }

  @Nested
  inner class DrainAll {
    @Test
    fun `drainAll cancels jobs and returns in-flight items`() {
      val cachingJob = Job()
      val pendingJob = Job()

      registry.register("book-caching", cachingJob)
      registry.updateStatus(detailedItem("book-caching"), CacheState(CacheStatus.Caching))

      registry.register("book-pending", pendingJob)

      val completedJob = Job()
      registry.register("book-completed", completedJob)
      registry.updateStatus(detailedItem("book-completed"), CacheState(CacheStatus.Completed))
      registry.settle("book-completed", completedJob)

      val interrupted = registry.drainAll()

      assertEquals(setOf("book-caching", "book-pending"), interrupted.toSet())
      assertTrue(cachingJob.isCancelled)
      assertTrue(pendingJob.isCancelled)
    }

    @Test
    fun `drainAll resets the whole session`() {
      registry.register("book-1", Job())
      registry.updateStatus(detailedItem("book-1"), CacheState(CacheStatus.Caching))
      registry.markError()

      registry.drainAll()

      assertFalse(registry.inProgress())
      assertFalse(registry.hasErrors())
      assertTrue(registry.notificationItems().isEmpty())
    }

    @Test
    fun `drainAll on empty registry returns nothing`() {
      assertTrue(registry.drainAll().isEmpty())
    }
  }

  @Nested
  inner class CancelAll {
    @Test
    fun `cancelAll cancels jobs and returns every tracked item`() =
      runTest {
        val job = Job()
        registry.register("book-1", job)

        val completedJob = Job()
        registry.register("book-2", completedJob)
        registry.updateStatus(detailedItem("book-2"), CacheState(CacheStatus.Completed))
        registry.settle("book-2", completedJob)

        val cancelled = registry.cancelAll()

        assertEquals(setOf("book-1", "book-2"), cancelled.toSet())
        assertTrue(job.isCancelled)
        assertFalse(registry.inProgress())
      }

    @Test
    fun `cancelAll resets the error mark`() =
      runTest {
        registry.markError()

        registry.cancelAll()

        assertFalse(registry.hasErrors())
      }
  }

  private fun detailedItem(id: String) =
    DetailedItem(
      id = id,
      title = "Test Book",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = "lib-1",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
}
