package org.grakovne.lissen.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.sortedBySeriesPosition
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
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
    private val preferences: LibraryPreferences,
  ) : ViewModel() {
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

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

    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroups: StateFlow<Set<String>> = _expandedGroups.asStateFlow()

    // Per-key snapshot-backed state so only the item whose group changes recomposes,
    // instead of invalidating every visible grouped item on each whole-collection update.
    val groupBooks: SnapshotStateMap<String, List<Book>> = mutableStateMapOf()

    val groupLoading: SnapshotStateList<String> = mutableStateListOf()

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

    fun toggleGroup(entry: LibraryEntry) {
      val groupId = entry.groupId() ?: return
      Timber.d("User action: toggleGroup $groupId")

      when (groupId in _expandedGroups.value) {
        true -> {
          _expandedGroups.value = _expandedGroups.value - groupId
        }

        false -> {
          _expandedGroups.value = _expandedGroups.value + groupId
          viewModelScope.launch { fetchGroupBooks(entry) }
        }
      }
    }

    fun prefetchGroup(entry: LibraryEntry) {
      val groupId = entry.groupId() ?: return
      if (alreadyResolved(groupId)) {
        return
      }

      viewModelScope.launch {
        prefetchSemaphore.withPermit {
          fetchGroupBooks(entry)
        }
      }
    }

    fun resetGroupExpansion() {
      _expandedGroups.value = emptySet()
      groupBooks.clear()
      groupLoading.clear()
    }

    private fun LibraryEntry.groupId(): String? =
      when (this) {
        is LibraryEntry.SeriesEntry -> id
        is LibraryEntry.AuthorEntry -> id
        is LibraryEntry.BookEntry -> null
      }

    private fun alreadyResolved(groupId: String): Boolean = groupBooks.containsKey(groupId) || groupId in groupLoading

    private suspend fun fetchGroupBooks(entry: LibraryEntry) {
      val groupId = entry.groupId() ?: return
      if (alreadyResolved(groupId)) {
        return
      }

      val libraryId = preferences.getPreferredLibrary()?.id ?: return

      groupLoading.add(groupId)
      val result =
        when (entry) {
          is LibraryEntry.SeriesEntry -> mediaChannel.fetchSeriesItems(libraryId = libraryId, seriesId = entry.id)
          is LibraryEntry.AuthorEntry -> mediaChannel.fetchAuthorBooks(libraryId = libraryId, authorId = entry.id)
          is LibraryEntry.BookEntry -> null
        }

      result?.fold(
        onSuccess = { books ->
          val ordered =
            when (entry) {
              is LibraryEntry.SeriesEntry -> books.sortedBySeriesPosition()
              else -> books
            }
          groupBooks[groupId] = ordered
        },
        onFailure = { },
      )
      groupLoading.remove(groupId)
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

    val preferredLibraryType: StateFlow<LibraryType> =
      preferences
        .preferredLibraryTypeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), fetchPreferredLibraryType())

    fun fetchPreferredLibraryType() =
      preferences
        .getPreferredLibrary()
        ?.type
        ?: LibraryType.UNKNOWN

    fun refreshRecentListening() {
      Timber.d("User action: refreshRecentListening")
      viewModelScope.launch {
        withContext(dispatcher) {
          fetchRecentListening()
        }
      }
    }

    fun refreshLibrary() {
      Timber.d("User action: refreshLibrary")
      viewModelScope.launch {
        withContext(dispatcher) {
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
