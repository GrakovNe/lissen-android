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
    private var sessionErrored = false

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
      val cancelledJobs = jobs.values.toList()

      pending.clear()
      statuses.clear()
      jobs.clear()
      sessionErrored = false

      cancelledJobs.forEach { it.cancel() }
      cancelledJobs.joinAll()

      return itemIds
    }

    fun finishSession(forceError: Boolean = false): Boolean {
      val errored = forceError || sessionErrored || hasErrors()
      val activeJobs = jobs.values.toList()

      pending.clear()
      statuses.clear()
      jobs.clear()
      sessionErrored = false
      activeJobs.forEach { it.cancel() }

      return errored
    }

    fun settle(
      itemId: String,
      job: Job,
      errored: Boolean = false,
    ): Boolean {
      if (jobs[itemId] === job) {
        sessionErrored = sessionErrored || errored
        pending.remove(itemId)
        jobs.remove(itemId)
        return true
      }

      return false
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
