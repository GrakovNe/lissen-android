package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.library.paging.LibraryDefaultPagingSource
import org.grakovne.lissen.ui.screens.library.paging.LibrarySearchPagingSource
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel
  @Inject
  constructor(
    private val mediaChannel: LissenMediaProvider,
    private val preferences: LissenSharedPreferences,
  ) : ViewModel() {
    private val _recentBooks = MutableStateFlow<List<RecentBook>>(emptyList())
    val recentBooks: StateFlow<List<RecentBook>> = _recentBooks.asStateFlow()

    private val _recentBookUpdating = MutableStateFlow(false)
    val recentBookUpdating: StateFlow<Boolean> = _recentBookUpdating.asStateFlow()

    private val _searchRequested = MutableStateFlow(false)
    val searchRequested: StateFlow<Boolean> = _searchRequested.asStateFlow()

    private val _searchToken = MutableStateFlow(EMPTY_SEARCH)
    val searchToken: StateFlow<String> = _searchToken.asStateFlow()

    private var defaultPagingSource: PagingSource<Int, LibraryEntry>? = null
    private var searchPagingSource: PagingSource<Int, LibraryEntry>? = null

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _expandedSeries = MutableStateFlow<Set<String>>(emptySet())
    val expandedSeries: StateFlow<Set<String>> = _expandedSeries.asStateFlow()

    private val _seriesBooks = MutableStateFlow<Map<String, List<Book>>>(emptyMap())
    val seriesBooks: StateFlow<Map<String, List<Book>>> = _seriesBooks.asStateFlow()

    private val _seriesLoading = MutableStateFlow<Set<String>>(emptySet())
    val seriesLoading: StateFlow<Set<String>> = _seriesLoading.asStateFlow()

    private val prefetchSemaphore = Semaphore(MAX_CONCURRENT_PREFETCH)

    private val pageConfig =
      PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = PAGE_SIZE,
      )

    fun getPager(isSearchRequested: Boolean) =
      when (isSearchRequested) {
        true -> searchPager
        false -> libraryPager
      }

    private val searchPager: Flow<PagingData<LibraryEntry>> =
      combine(
        _searchToken.debounce(SEARCH_DEBOUNCE_MILLIS),
        _searchRequested,
      ) { token, requested ->
        Pair(token, requested)
      }.flatMapLatest { (token, _) ->
        Pager(
          config = pageConfig,
          pagingSourceFactory = {
            val source =
              LibrarySearchPagingSource(
                preferences = preferences,
                mediaChannel = mediaChannel,
                searchToken = token,
                limit = PAGE_SEARCH_SIZE,
              ) { _totalCount.value = it }

            searchPagingSource = source
            source
          },
        ).flow
      }.cachedIn(viewModelScope)

    private val libraryPager: Flow<PagingData<LibraryEntry>> by lazy {
      Pager(
        config = pageConfig,
        pagingSourceFactory = {
          val source = LibraryDefaultPagingSource(preferences, mediaChannel) { _totalCount.value = it }
          defaultPagingSource = source

          source
        },
      ).flow.cachedIn(viewModelScope)
    }

    fun requestSearch() {
      Timber.d("User action: requestSearch")
      _searchRequested.value = true
    }

    fun dismissSearch() {
      Timber.d("User action: dismissSearch")
      _searchRequested.value = false
      _searchToken.value = EMPTY_SEARCH
    }

    fun updateSearch(token: String) {
      viewModelScope.launch { _searchToken.emit(token) }
    }

    fun toggleSeries(series: LibraryEntry.SeriesEntry) {
      Timber.d("User action: toggleSeries ${series.id}")

      when (series.id in _expandedSeries.value) {
        true -> {
          _expandedSeries.value = _expandedSeries.value - series.id
        }

        false -> {
          _expandedSeries.value = _expandedSeries.value + series.id
          // The user's expand fetches immediately, bypassing the prefetch concurrency limit.
          viewModelScope.launch { fetchSeriesBooks(series) }
        }
      }
    }

    /**
     * Warms the books of a series before it is expanded. Triggered once a series row has dwelled on
     * screen; throttled by [prefetchSemaphore] so fast scrolling cannot flood the network.
     */
    fun prefetchSeries(series: LibraryEntry.SeriesEntry) {
      if (alreadyResolved(series.id)) {
        return
      }

      viewModelScope.launch {
        prefetchSemaphore.withPermit {
          fetchSeriesBooks(series)
        }
      }
    }

    fun resetSeriesExpansion() {
      _expandedSeries.value = emptySet()
      _seriesBooks.value = emptyMap()
      _seriesLoading.value = emptySet()
    }

    private fun alreadyResolved(seriesId: String): Boolean = _seriesBooks.value.containsKey(seriesId) || seriesId in _seriesLoading.value

    private suspend fun fetchSeriesBooks(series: LibraryEntry.SeriesEntry) {
      if (alreadyResolved(series.id)) {
        return
      }

      val libraryId = preferences.getPreferredLibrary()?.id ?: return

      _seriesLoading.value = _seriesLoading.value + series.id
      mediaChannel
        .fetchSeriesItems(libraryId = libraryId, seriesId = series.id)
        .fold(
          onSuccess = { books -> _seriesBooks.value = _seriesBooks.value + (series.id to books) },
          onFailure = { },
        )
      _seriesLoading.value = _seriesLoading.value - series.id
    }

    fun applyLinkedSearch(token: String) {
      Timber.d("User action: applyLinkedSearch")
      _searchToken.value = token
      _searchRequested.value = true
    }

    fun fetchPreferredLibraryTitle(): String? =
      preferences
        .getPreferredLibrary()
        ?.title

    fun fetchPreferredLibraryType() =
      preferences
        .getPreferredLibrary()
        ?.type
        ?: LibraryType.UNKNOWN

    fun refreshRecentListening() {
      Timber.d("User action: refreshRecentListening")
      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          fetchRecentListening()
        }
      }
    }

    fun refreshLibrary() {
      Timber.d("User action: refreshLibrary")
      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          when (searchRequested.value) {
            true -> searchPagingSource?.invalidate()
            else -> defaultPagingSource?.invalidate()
          }
        }
      }
    }

    fun fetchRecentListening() {
      _recentBookUpdating.value = true

      val preferredLibrary =
        preferences.getPreferredLibrary()?.id ?: run {
          _recentBookUpdating.value = false
          return
        }

      viewModelScope.launch {
        mediaChannel
          .fetchRecentListenedBooks(preferredLibrary)
          .fold(
            onSuccess = {
              _recentBooks.value = it
              _recentBookUpdating.value = false
            },
            onFailure = {
              _recentBookUpdating.value = false
            },
          )
      }
    }

    companion object {
      private const val EMPTY_SEARCH = ""
      private const val PAGE_SIZE = 20
      private const val PAGE_SEARCH_SIZE = 50
      private const val SEARCH_DEBOUNCE_MILLIS = 300L
      private const val MAX_CONCURRENT_PREFETCH = 3
    }
  }
