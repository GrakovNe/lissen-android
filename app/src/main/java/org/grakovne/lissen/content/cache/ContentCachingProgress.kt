package org.grakovne.lissen.content.cache

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.grakovne.lissen.viewmodel.CacheProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentCachingProgress @Inject constructor() {
    private val _progressFlow = MutableSharedFlow<Pair<String, CacheProgress>>(replay = 1)
    val progressFlow = _progressFlow.asSharedFlow()

    suspend fun emit(itemId: String, progress: CacheProgress) {
        _progressFlow.emit(itemId to progress)
    }
}
