package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.Job
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem

class CachingSessionRegistry {
  private val jobs = mutableMapOf<String, Job>()
  private val statuses = LinkedHashMap<String, Pair<DetailedItem, CacheState>>()
  private val pending = mutableSetOf<String>()

  fun register(
    itemId: String,
    job: Job,
  ) {
    jobs.remove(itemId)?.cancel()
    jobs[itemId] = job
    pending.add(itemId)
  }

  fun cancel(itemId: String): Boolean {
    pending.remove(itemId)
    statuses.remove(itemId)

    val job = jobs.remove(itemId) ?: return false
    job.cancel()
    return true
  }

  fun settle(itemId: String) {
    pending.remove(itemId)
  }

  fun updateStatus(
    item: DetailedItem,
    state: CacheState,
  ) {
    pending.remove(item.id)
    statuses[item.id] = item to state
  }

  fun notificationItems(): List<Pair<DetailedItem, CacheState>> = statuses.values.toList()

  fun inProgress(): Boolean = pending.isNotEmpty() || statuses.values.any { (_, state) -> state.status == CacheStatus.Caching }

  fun hasErrors(): Boolean = statuses.values.any { (_, state) -> state.status == CacheStatus.Error }
}
