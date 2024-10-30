package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.LocalCacheConfiguration
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.library.paging.LibraryPagingSource
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaChannel: LissenMediaProvider,
    private val preferences: LissenSharedPreferences,
    private val localCacheConfiguration: LocalCacheConfiguration
) : ViewModel() {

    private val _recentBooks = MutableLiveData<List<RecentBook>>(emptyList())
    val recentBooks: LiveData<List<RecentBook>> = _recentBooks

    private val _recentBookUpdating = MutableLiveData(false)
    val recentBookUpdating: LiveData<Boolean> = _recentBookUpdating

    private val _hiddenBooks = MutableStateFlow<List<String>>(emptyList())
    val hiddenBooks: StateFlow<List<String>> = _hiddenBooks

    private var currentPagingSource: LibraryPagingSource? = null

    val libraryPager: Flow<PagingData<Book>> by lazy {
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = PAGE_SIZE
            ),
            pagingSourceFactory = {
                LibraryPagingSource(preferences, mediaChannel).also { currentPagingSource = it }
            }
        ).flow.cachedIn(viewModelScope)
    }

    fun isVisible(bookId: String): Boolean {
        return when (localCacheConfiguration.localCacheUsing()) {
            true -> !hiddenBooks.value.contains(bookId)
            false -> true
        }
    }

    fun refreshRecentListening() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                launch(Dispatchers.IO) { fetchRecentListening() }
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                launch(Dispatchers.IO) { currentPagingSource?.invalidate() }
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

        private const val PAGE_SIZE = 20
    }
}
