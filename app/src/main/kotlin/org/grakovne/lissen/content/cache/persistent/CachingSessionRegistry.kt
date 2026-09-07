package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachingSessionRegistry
  @Inject
  constructor() {
    private val jobs = mutableMapOf<String, Job>()
    private val statuses = LinkedHashMap<String, Pair<DetailedItem, CacheState>>()
    private val pending = mutableSetOf<String>()
    private var errorMarked = false

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

    suspend fun cancelAll(): List<String> {
      val itemIds = (jobs.keys + statuses.keys + pending).toList()

      teardown().joinAll()

      return itemIds
    }

    fun drainAll(): List<String> {
      val inFlight =
        (
          pending +
            jobs.keys +
            statuses.filterValues { (_, state) -> state.status == CacheStatus.Caching }.keys
        ).toList()

      teardown()

      return inFlight
    }

    private fun teardown(): List<Job> {
      val cancelledJobs = jobs.values.toList()

      clear()

      cancelledJobs.forEach { it.cancel() }
      return cancelledJobs
    }

    fun markError() {
      errorMarked = true
    }

    fun settle(
      itemId: String,
      job: Job,
    ) {
      if (jobs[itemId] === job) {
        pending.remove(itemId)
        jobs.remove(itemId)
      }
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

    fun hasErrors(): Boolean = errorMarked || statuses.values.any { (_, state) -> state.status == CacheStatus.Error }

    private fun clear() {
      pending.clear()
      statuses.clear()
      jobs.clear()
      errorMarked = false
    }
  }
