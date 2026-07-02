package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.Job
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem

class CachingSessionRegistry {
  private val jobs = mutableMapOf<String, Job>()
  private val statuses = LinkedHashMap<String, Pair<DetailedItem, CacheState>>()

  fun register(
    itemId: String,
    job: Job,
  ) {
    jobs.remove(itemId)?.cancel()
    jobs[itemId] = job
  }

  fun cancel(itemId: String): Boolean {
    val job = jobs.remove(itemId) ?: return false
    job.cancel()
    statuses.remove(itemId)
    return true
  }

  fun updateStatus(
    item: DetailedItem,
    state: CacheState,
  ) {
    statuses[item.id] = item to state
  }

  fun notificationItems(): List<Pair<DetailedItem, CacheState>> = statuses.values.toList()

  fun inProgress(): Boolean = statuses.values.any { (_, state) -> state.status == CacheStatus.Caching }

  fun hasErrors(): Boolean = statuses.values.any { (_, state) -> state.status == CacheStatus.Error }
}
