package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.Job
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CachingSessionRegistryTest {
  private fun item(
    id: String = "book-1",
    progress: MediaProgress? = null,
  ) = DetailedItem(
    id = id,
    title = "Title",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = emptyList(),
    progress = progress,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  @Test
  fun `cancel by id works when the stop request carries a stale item snapshot`() {
    val registry = CachingSessionRegistry()
    val job = Job()

    val cachingSnapshot = item(progress = MediaProgress(currentTime = 10.0, isFinished = false, lastUpdate = 1L))
    registry.register(cachingSnapshot.id, job)

    val staleSnapshot = item(progress = MediaProgress(currentTime = 99.0, isFinished = false, lastUpdate = 2L))

    assertTrue(registry.cancel(staleSnapshot.id))
    assertTrue(job.isCancelled)
  }

  @Test
  fun `cancel of unknown item reports nothing to stop`() {
    val registry = CachingSessionRegistry()

    assertFalse(registry.cancel("missing"))
  }

  @Test
  fun `registering the same item twice cancels the previous job`() {
    val registry = CachingSessionRegistry()
    val first = Job()
    val second = Job()

    registry.register(item().id, first)
    registry.register(item().id, second)

    assertTrue(first.isCancelled)
    assertFalse(second.isCancelled)
  }

  @Test
  fun `registered download counts as in progress before its first status update`() {
    val registry = CachingSessionRegistry()

    registry.register("book-1", Job())

    assertTrue(registry.inProgress())
  }

  @Test
  fun `settling a registration without status leaves nothing in progress`() {
    val registry = CachingSessionRegistry()
    val job = Job()

    registry.register("book-1", job)
    registry.settle("book-1", job)

    assertFalse(registry.inProgress())
  }

  @Test
  fun `settling a stale job does not clear the newer registration`() {
    val registry = CachingSessionRegistry()
    val first = Job()
    val second = Job()

    registry.register("book-1", first)
    registry.register("book-1", second)

    registry.settle("book-1", first)

    assertTrue(registry.inProgress())
  }

  @Test
  fun `first status update replaces the pending registration`() {
    val registry = CachingSessionRegistry()

    registry.register("book-1", Job())
    registry.updateStatus(item(id = "book-1"), CacheState(CacheStatus.Completed))

    assertFalse(registry.inProgress())
  }

  @Test
  fun `error of one item keeps the other download in progress`() {
    val registry = CachingSessionRegistry()

    registry.updateStatus(item(id = "book-1"), CacheState(CacheStatus.Error))
    registry.updateStatus(item(id = "book-2"), CacheState(CacheStatus.Caching))

    assertTrue(registry.inProgress())
    assertTrue(registry.hasErrors())
  }

  @Test
  fun `finished downloads leave nothing in progress`() {
    val registry = CachingSessionRegistry()

    registry.updateStatus(item(id = "book-1"), CacheState(CacheStatus.Completed))

    assertFalse(registry.inProgress())
    assertFalse(registry.hasErrors())
  }

  @Test
  fun `cancel removes the item from notification payload`() {
    val registry = CachingSessionRegistry()
    val cached = item()

    registry.register(cached.id, Job())
    registry.updateStatus(cached, CacheState(CacheStatus.Caching))
    registry.cancel(cached.id)

    assertEquals(emptyList<Pair<DetailedItem, CacheState>>(), registry.notificationItems())
  }

  @Test
  fun `finishing an errored session does not poison the next session`() {
    val registry = CachingSessionRegistry()

    registry.updateStatus(item(id = "failed"), CacheState(CacheStatus.Error))

    assertTrue(registry.finishSession())
    assertTrue(registry.notificationItems().isEmpty())

    registry.updateStatus(item(id = "completed"), CacheState(CacheStatus.Completed))

    assertFalse(registry.finishSession())
    assertTrue(registry.notificationItems().isEmpty())
  }

  @Test
  fun `finishing a session cancels registrations which are still active`() {
    val registry = CachingSessionRegistry()
    val job = Job()
    registry.register("book-1", job)

    assertFalse(registry.finishSession())

    assertTrue(job.isCancelled)
    assertFalse(registry.inProgress())
  }

  @Test
  fun `forced session failure is reported without a stored error status`() {
    val registry = CachingSessionRegistry()

    assertTrue(registry.finishSession(forceError = true))
    assertTrue(registry.notificationItems().isEmpty())
  }

  @Test
  fun `an early failure is retained until the concurrent session finishes`() {
    val registry = CachingSessionRegistry()
    val job = Job()
    val concurrentJob = Job()
    registry.register("book-2", job)
    registry.register("book-3", concurrentJob)
    registry.settle("book-2", job, errored = true)

    assertTrue(registry.inProgress())

    registry.updateStatus(item(id = "book-3"), CacheState(CacheStatus.Completed))
    registry.settle("book-3", concurrentJob)

    assertTrue(registry.finishSession())
    assertFalse(registry.finishSession())
  }

  @Test
  fun `failure from a replaced job does not poison its replacement`() {
    val registry = CachingSessionRegistry()
    val replacedJob = Job()
    val replacementJob = Job()
    registry.register("book-1", replacedJob)
    registry.register("book-1", replacementJob)

    assertFalse(registry.settle("book-1", replacedJob, errored = true))

    registry.updateStatus(item(id = "book-1"), CacheState(CacheStatus.Completed))
    assertTrue(registry.settle("book-1", replacementJob))
    assertFalse(registry.finishSession())
  }
}
