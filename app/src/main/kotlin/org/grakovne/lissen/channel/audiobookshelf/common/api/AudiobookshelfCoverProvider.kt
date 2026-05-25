package org.grakovne.lissen.channel.audiobookshelf.common.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import org.grakovne.lissen.channel.common.OperationResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfCoverProvider
  @Inject
  constructor(
    private val audioBookShelfApiService: AudioBookShelfApiService,
  ) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<Pair<String, Int?>, Deferred<OperationResult<Buffer>>>()

    suspend fun fetchCover(
      itemId: String,
      width: Int?,
    ): OperationResult<Buffer> {
      val key = itemId to width
      val deferred =
        mutex.withLock {
          inFlight[key]?.takeIf { it.isActive }
            ?: scope
              .async { fetch(itemId, width) }
              .also { inFlight[key] = it }
        }
      return deferred.await()
    }

    private suspend fun fetch(
      itemId: String,
      width: Int?,
    ): OperationResult<Buffer> =
      audioBookShelfApiService
        .makeRequest {
          when (width == null) {
            true -> it.getItemCover(itemId = itemId)
            false -> it.getItemCover(itemId = itemId, width)
          }
        }.map { response ->
          withContext(Dispatchers.IO) {
            response.use {
              Buffer().apply { writeAll(it.source()) }
            }
          }
        }
  }
