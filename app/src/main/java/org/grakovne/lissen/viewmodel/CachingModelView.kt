package org.grakovne.lissen.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.cache.CacheState
import org.grakovne.lissen.content.cache.ContentCachingManager
import org.grakovne.lissen.content.cache.ContentCachingProgress
import org.grakovne.lissen.content.cache.ContentCachingService
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class CachingModelView
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val contentCachingProgress: ContentCachingProgress,
    private val contentCachingManager: ContentCachingManager,
    private val preferences: LissenSharedPreferences,
  ) : ViewModel() {
    private val _bookCachingProgress = mutableMapOf<String, MutableStateFlow<CacheState>>()

    init {
      viewModelScope.launch {
        contentCachingProgress.statusFlow.collect { (item, progress) ->
          val flow =
            _bookCachingProgress.getOrPut(item.id) {
              MutableStateFlow(progress)
            }
          flow.value = progress
        }
      }
    }

    fun cache(
      mediaItemId: String,
      currentPosition: Double,
      option: DownloadOption,
    ) {
      val task =
        ContentCachingTask(
          itemId = mediaItemId,
          options = option,
          currentPosition = currentPosition,
        )

      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          putExtra(ContentCachingService.CACHING_TASK_EXTRA, task as Serializable)
        }

      context.startForegroundService(intent)
    }

    fun getProgress(bookId: String) =
      _bookCachingProgress
        .getOrPut(bookId) { MutableStateFlow(CacheState(CacheStatus.Idle)) }

    fun dropCache(bookId: String) {
      viewModelScope
        .launch {
          contentCachingManager.dropCache(bookId)
          _bookCachingProgress.remove(bookId)
        }
    }

    fun toggleCacheForce() {
      when (localCacheUsing()) {
        true -> preferences.disableForceCache()
        false -> preferences.enableForceCache()
      }
    }

    fun localCacheUsing() = preferences.isForceCache()

    fun provideCacheState(bookId: String): LiveData<Boolean> = contentCachingManager.hasMetadataCached(bookId)
  }
