package org.grakovne.lissen.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.ContentCachingForegroundService
import org.grakovne.lissen.content.cache.ContentCachingService
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class ContentCachingModelView @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentCachingService: ContentCachingService,
    private val preferences: LissenSharedPreferences,
) : ViewModel() {

    private val _bookCachingProgress = mutableMapOf<String, MutableStateFlow<CacheProgress>>()

    fun requestCache(
        mediaItemId: String,
        currentPosition: Double,
        option: DownloadOption,
    ) {



        val task = ContentCachingTask(
            itemId = mediaItemId,
            options = option,
            currentPosition = currentPosition
        )

        val intent = Intent(context, ContentCachingForegroundService::class.java).apply {
            putExtra(ContentCachingForegroundService.CACHING_TASK_EXTRA, task as Serializable)
        }

        context.startForegroundService(intent)

//        viewModelScope.launch {
//            contentCachingService
//                .cacheMediaItem(
//                    mediaItemId = mediaItemId,
//                    option = option,
//                    channel = mediaProvider.providePreferredChannel(),
//                    currentTotalPosition = currentPosition,
//                )
//                .collect { _bookCachingProgress[mediaItemId]?.value = it }
//        }
    }

    fun getCacheProgress(bookId: String) = _bookCachingProgress
        .getOrPut(bookId) {
            MutableStateFlow(CacheProgress.Idle)
        }

    fun dropCache(bookId: String) {
        viewModelScope.launch {
            contentCachingService
                .dropCache(bookId)
                .collect { _bookCachingProgress[bookId]?.value = it }
        }
    }

    fun toggleCacheForce() {
        when (localCacheUsing()) {
            true -> preferences.disableForceCache()
            false -> preferences.enableForceCache()
        }
    }

    fun localCacheUsing() = preferences.isForceCache()

    fun provideCacheState(bookId: String): LiveData<Boolean> =
        contentCachingService.hasMetadataCached(bookId)
}
