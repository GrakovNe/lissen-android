package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentCachingProgress
  @Inject
  constructor() {
    private val _statusFlow = MutableSharedFlow<Pair<String, CacheState>>(replay = 1)
    val statusFlow = _statusFlow.asSharedFlow()

    suspend fun emit(
      itemId: String,
      progress: CacheState,
    ) {
      _statusFlow.emit(itemId to progress)
    }
  }
