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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.LocalCacheConfiguration
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.library.paging.LibraryDefaultPagingSource
import org.grakovne.lissen.ui.screens.library.paging.LibrarySearchPagingSource
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel @Inject constructor(
    private val mediaChannel: LissenMediaProvider,
    private val preferences: LissenSharedPreferences,
    private val localCacheConfiguration: LocalCacheConfiguration
) : ViewModel() {

    private val _recentBooks = MutableLiveData<List<RecentBook>>(emptyList())
    val recentBooks: LiveData<List<RecentBook>> = _recentBooks

    private val _recentBookUpdating = MutableLiveData(false)
    val recentBookUpdating: LiveData<Boolean> = _recentBookUpdating

    private val _searchRequested = MutableLiveData(false)
    val searchRequested: LiveData<Boolean> = _searchRequested

    private val _searchToken = MutableStateFlow(EMPTY_SEARCH)

    private val _hiddenBooks = MutableStateFlow<List<String>>(emptyList())
    val hiddenBooks: StateFlow<List<String>> = _hiddenBooks

    private var currentPagingSource: PagingSource<Int, Book>? = null
    private val _libraryPager = MutableStateFlow(createPager())

    val libraryPager: Flow<PagingData<Book>> = _libraryPager
        .flatMapLatest { it.flow.cachedIn(viewModelScope) }

    init {
        viewModelScope
            .launch {
                val combinedFlow = combine(
                    _searchRequested.asFlow(),
                    _searchToken
                ) { searchRequested, searchToken -> searchRequested to searchToken }

                combinedFlow
                    .distinctUntilChanged()
                    .collect { (searchRequested, searchToken) ->
                        _libraryPager.value = createPager(searchRequested, searchToken)
                    }
            }
    }

    private fun createPager(searchRequested: Boolean = false, searchToken: String = EMPTY_SEARCH): Pager<Int, Book> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = PAGE_SIZE
            ),
            pagingSourceFactory = {
                when (searchRequested) {
                    true -> LibrarySearchPagingSource(preferences, mediaChannel, searchToken).also { currentPagingSource = it }
                    else -> LibraryDefaultPagingSource(preferences, mediaChannel).also { currentPagingSource = it }
                }
            }
        )
    }


    fun isVisible(bookId: String): Boolean {
        return when (localCacheConfiguration.localCacheUsing()) {
            true -> !hiddenBooks.value.contains(bookId)
            false -> true
        }
    }

    fun requestSearch() {
        _searchRequested.value = true
    }

    fun dismissSearch() {
        _searchRequested.value = false
        _searchToken.value = EMPTY_SEARCH
    }

    fun updateSearch(token: String) {
        viewModelScope.launch {
            _searchToken.emit(token)
            currentPagingSource?.invalidate()
        }
    }

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
                currentPagingSource?.invalidate()
            }
        }
    }

    fun fetchRecentListening() {
        _recentBookUpdating.postValue(true)

        val preferredLibrary = preferences.getPreferredLibrary()?.id ?: run {
            _recentBookUpdating.postValue(false)
            return
        }

        viewModelScope.launch {
            mediaChannel
                .fetchRecentListenedBooks(preferredLibrary)
                .fold(
                    onSuccess = {
                        _recentBooks.postValue(it)
                        _recentBookUpdating.postValue(false)
                    },
                    onFailure = {
                        _recentBookUpdating.postValue(false)
                    }
                )
        }
    }

    fun hideBook(bookId: String) {
        _hiddenBooks.value += bookId
    }

    fun dropHiddenBooks() {
        _hiddenBooks.value = emptyList()
    }

    companion object {

        private const val EMPTY_SEARCH = ""
        private const val PAGE_SIZE = 20
    }
}
