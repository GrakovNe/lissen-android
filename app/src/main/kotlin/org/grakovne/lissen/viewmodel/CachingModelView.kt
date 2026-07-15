package org.grakovne.lissen.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
import org.grakovne.lissen.content.cache.persistent.ContentCachingProgress
import org.grakovne.lissen.content.cache.persistent.ContentCachingService
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.content.cache.temporary.SeriesCoverProvider
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.ui.screens.settings.advanced.cache.CachedItemsPageSource
import timber.log.Timber
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class CachingModelView
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val localCacheRepository: LocalCacheRepository,
    private val contentCachingProgress: ContentCachingProgress,
    private val contentCachingManager: ContentCachingManager,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val cachedCoverProvider: CachedCoverProvider,
    private val seriesCoverProvider: SeriesCoverProvider,
  ) : ViewModel() {
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    val forceCache = libraryPreferences.forceCacheFlow

    private val _bookCachingProgress = mutableMapOf<String, MutableStateFlow<CacheState>>()

    private val pageConfig =
      PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = PAGE_SIZE,
      )

    private var pageSource: PagingSource<Int, DetailedItem>? = null
    val libraryPager: Flow<PagingData<DetailedItem>> by lazy {
      Pager(
        config = pageConfig,
        pagingSourceFactory = {
          val source = CachedItemsPageSource(localCacheRepository) { _totalCount.value = it }

          pageSource = source
          source
        },
      ).flow.cachedIn(viewModelScope)
    }

    init {
      viewModelScope.launch {
        contentCachingProgress.statusFlow.collect { (itemId, progress) ->
          val flow =
            _bookCachingProgress.getOrPut(itemId) {
              MutableStateFlow(progress)
            }
          flow.value = progress
        }
      }
    }

    suspend fun clearShortTermCache() {
      withContext(Dispatchers.IO) {
        cachedCoverProvider.clearCache()
        seriesCoverProvider.clearCache()
      }
    }

    fun cache(
      mediaItem: DetailedItem,
      currentPosition: Double,
      option: DownloadOption,
    ) {
      Timber.d("User action: cache ${mediaItem.id}, option=$option, position=${currentPosition.toInt()}s")
      val task =
        ContentCachingTask(
          itemId = mediaItem.id,
          options = option,
          currentPosition = currentPosition,
        )

      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          action = ContentCachingService.CACHE_ITEM_ACTION
          putExtra(ContentCachingService.CACHING_TASK_EXTRA, task as Serializable)
        }

      context.startForegroundService(intent)
    }

    fun getProgress(bookId: String) =
      _bookCachingProgress
        .getOrPut(bookId) { MutableStateFlow(CacheState(CacheStatus.Idle)) }

    suspend fun dropCache(bookId: String) {
      Timber.d("User action: dropCache $bookId")
      contentCachingManager.dropCache(bookId)
    }

    fun stopCaching(item: DetailedItem) {
      Timber.d("User action: stopCaching ${item.id}")
      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          action = ContentCachingService.STOP_CACHING_ACTION
          putExtra(ContentCachingService.CACHING_ITEM_ID, item.id)
        }

      context.startForegroundService(intent)
    }

    suspend fun dropCache(
      item: DetailedItem,
      chapter: PlayingChapter,
    ) {
      Timber.d("User action: dropCache ${item.id}, chapter=${chapter.id}")
      contentCachingManager.dropCache(item, chapter)
    }

    fun toggleCacheForce() {
      Timber.d("User action: toggleCacheForce (current=${localCacheUsing()})")
      when (localCacheUsing()) {
        true -> libraryPreferences.disableForceCache()
        false -> libraryPreferences.enableForceCache()
      }
    }

    fun localCacheUsing() = libraryPreferences.isForceCache()

    fun getDownloadChaptersCount() = downloadPreferences.getDownloadChaptersCount()

    fun saveDownloadChaptersCount(count: Int) = downloadPreferences.saveDownloadChaptersCount(count)

    fun provideCacheState(bookId: String): Flow<Boolean> = contentCachingManager.hasMetadataCached(bookId)

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ): Flow<Boolean> = contentCachingManager.hasMetadataCached(bookId, chapterId)

    fun provideCachedChapterIds(bookId: String): Flow<List<String>> = contentCachingManager.provideCachedChapterIds(bookId)

    fun fetchCachedItems() {
      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          pageSource?.invalidate()
        }
      }
    }

    suspend fun fetchLatestUpdate(libraryId: String) = localCacheRepository.fetchLatestUpdate(libraryId)

    companion object {
      private const val PAGE_SIZE = 20
    }
  }
