package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.content.BookRepository
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.library.paging.LibraryDefaultPagingSource
import org.grakovne.lissen.ui.screens.library.paging.LibrarySearchPagingSource
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel
  @Inject
  constructor(
    private val bookRepository: BookRepository,
    private val preferences: LissenSharedPreferences,
    private val networkService: NetworkService,
  ) : ViewModel() {
    private val _recentBooks = MutableLiveData<List<RecentBook>>(emptyList())
    val recentBooks: LiveData<List<RecentBook>> = _recentBooks

    private val _recentBookUpdating = MutableLiveData(false)
    val recentBookUpdating: LiveData<Boolean> = _recentBookUpdating

    private val _searchRequested = MutableLiveData(false)
    val searchRequested: LiveData<Boolean> = _searchRequested

    private val _searchToken = MutableStateFlow(EMPTY_SEARCH)

    private val defaultPagingSource = MutableStateFlow<PagingSource<Int, Book>?>(null)
    private var searchPagingSource: PagingSource<Int, Book>? = null

    private val _totalCount = MutableLiveData<Int>()
    val totalCount: LiveData<Int> = _totalCount

    private val pageConfig =
      PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = PAGE_SIZE,
      )

    private val downloadedOnlyFlow =
      combine(
        networkService.isServerAvailable,
        preferences.forceCacheFlow,
      ) { isServerAvailable, isForceCache ->
        !isServerAvailable || isForceCache
      }

    private var currentLibraryId = ""
    private var currentOrdering = LibraryOrderingConfiguration.default
    private var localCacheUpdatedAt = 0L

    fun checkRefreshNeeded(
      itemCount: Int,
      latestLocalUpdate: Long?,
      isLocalCacheUsing: Boolean,
    ) {
      val emptyContent = itemCount == 0
      val libraryChanged = currentLibraryId != (preferences.getPreferredLibrary()?.id ?: "")
      val orderingChanged = currentOrdering != preferences.getLibraryOrdering()
      val localCacheUpdated = latestLocalUpdate?.let { it > localCacheUpdatedAt } ?: true

      if (emptyContent || libraryChanged || orderingChanged || (isLocalCacheUsing && localCacheUpdated)) {
        refreshRecentListening()
        refreshLibrary()

        currentLibraryId = preferences.getPreferredLibrary()?.id ?: ""
        currentOrdering = preferences.getLibraryOrdering()
        localCacheUpdatedAt = latestLocalUpdate ?: 0L
      }
    }

    init {
      viewModelScope.launch {
        downloadedOnlyFlow.collect { refreshRecentListening() }
      }

      viewModelScope.launch {
        combine(
          preferences.preferredLibraryIdFlow,
          downloadedOnlyFlow,
        ) { libraryId, downloadedOnly ->
          Pair(libraryId, downloadedOnly)
        }.flatMapLatest { (libraryId, _) ->
          // When downloadedOnly changes, recent books flow in CachedBookRepository
          // (which BookRepository delegates to) handles sorting/filtering if needed.
          // Note: BookRepository.fetchRecentListenedBooksFlow checks isOffline internally one-shot,
          // but we want it to be reactive.
          // For now, let's just trigger the flow. Ideally, BookRepository should handle the isOffline check reactively too.
          // But re-collecting here when downloadedOnlyFlow emits will re-call fetchRecentListenedBooksFlow,
          // effectively re-checking the isOffline state.
          bookRepository.fetchRecentListenedBooksFlow(libraryId ?: "")
        }.collect {
          _recentBooks.postValue(it)
        }
      }
    }

    fun getPager(isSearchRequested: Boolean) =
      when (isSearchRequested) {
        true -> searchPager
        false -> libraryPager
      }

    private val searchPager: Flow<PagingData<Book>> =
      combine(
        _searchToken,
        searchRequested.asFlow(),
      ) { token, requested ->
        Pair(token, requested)
      }.flatMapLatest { (token, _) ->
        Pager(
          config = pageConfig,
          pagingSourceFactory = {
            val source =
              LibrarySearchPagingSource(
                preferences = preferences,
                bookRepository = bookRepository,
                searchToken = token,
                limit = PAGE_SEARCH_SIZE,
              ) { _totalCount.postValue(it) }

            searchPagingSource = source
            source
          },
        ).flow
      }.cachedIn(viewModelScope)

    private val libraryPager: Flow<PagingData<Book>> =
      combine(
        preferences.preferredLibraryIdFlow,
        downloadedOnlyFlow,
      ) { libraryId, downloadedOnly ->
        Pair(libraryId, downloadedOnly)
      }.onEach { (libraryId, downloadedOnly) ->
        if (!downloadedOnly && libraryId != null) {
          syncLibrary(libraryId)
        }
      }.flatMapLatest { (libraryId, downloadedOnly) ->
        Pager(
          config = pageConfig,
          pagingSourceFactory = {
            val source =
              LibraryDefaultPagingSource(
                preferences = preferences,
                bookRepository = bookRepository,
                downloadedOnly = downloadedOnly,
              ) { _totalCount.postValue(it) }
            defaultPagingSource.tryEmit(source)

            source
          },
        ).flow
      }.cachedIn(viewModelScope)

    private fun syncLibrary(libraryId: String) {
      viewModelScope.launch(Dispatchers.IO) {
        bookRepository.syncRepositories()
        refreshRecentListening()
        defaultPagingSource.value?.invalidate()
      }
    }

    fun requestSearch() {
      _searchRequested.postValue(true)
    }

    fun dismissSearch() {
      _searchRequested.postValue(false)
      _searchToken.value = EMPTY_SEARCH
    }

    fun updateSearch(token: String) {
      viewModelScope.launch { _searchToken.emit(token) }
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
      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          fetchRecentListening()
        }
      }
    }

    fun refreshLibrary() {
      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          when (searchRequested.value) {
            true -> searchPagingSource?.invalidate()
            else -> defaultPagingSource.value?.invalidate()
          }
        }
      }
    }

    fun fetchRecentListening() {
      _recentBookUpdating.postValue(true)

      val preferredLibrary =
        preferences.getPreferredLibrary()?.id ?: run {
          _recentBookUpdating.postValue(false)
          return
        }

      viewModelScope.launch {
        bookRepository.fetchRecentListenedBooks(preferredLibrary)
        _recentBookUpdating.postValue(false)
      }
    }

    companion object {
      private const val EMPTY_SEARCH = ""
      private const val PAGE_SIZE = 20
      private const val PAGE_SEARCH_SIZE = 50
    }
  }
