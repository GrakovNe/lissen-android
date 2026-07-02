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
}
