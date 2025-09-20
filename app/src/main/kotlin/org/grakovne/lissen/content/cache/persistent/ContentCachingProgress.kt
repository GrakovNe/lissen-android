package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.grakovne.lissen.lib.domain.DetailedItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentCachingProgress
  @Inject
  constructor() {
    private val _statusFlow = MutableSharedFlow<Pair<DetailedItem, CacheState>>(replay = 1)
    val statusFlow = _statusFlow.asSharedFlow()

    suspend fun emit(
      item: DetailedItem,
      progress: CacheState,
    ) {
      _statusFlow.emit(item to progress)
    }
  }
