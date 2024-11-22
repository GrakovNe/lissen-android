package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.BookCachingService
import org.grakovne.lissen.content.cache.CacheProgress
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookCachedState
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject

@HiltViewModel
class CachingModelView @Inject constructor(
    private val cachingService: BookCachingService,
    private val preferences: LissenSharedPreferences,
    private val mediaProvider: LissenMediaProvider,
) : ViewModel() {

    private val _bookCachingProgress = mutableMapOf<String, MutableStateFlow<CacheProgress>>()

    fun provideCacheAction(book: Book): BookCacheAction? {
        return when (getCacheProgress(book.id).value) {
            CacheProgress.Caching -> null
            CacheProgress.Completed -> BookCacheAction.DROP
            CacheProgress.Error -> BookCacheAction.CACHE
            CacheProgress.Idle -> when (book.cachedState) {
                BookCachedState.ABLE_TO_CACHE -> BookCacheAction.CACHE
                BookCachedState.CACHED -> BookCacheAction.DROP
                BookCachedState.UNABLE_TO_CACHE -> null
            }

            CacheProgress.Removed -> BookCacheAction.CACHE
        }
    }

    fun dropCache(book: Book) {
        viewModelScope.launch {
            cachingService
                .removeBook(book)
                .collect {
                    _bookCachingProgress[book.id]?.value = it
                }
        }
    }

    fun cacheBook(book: Book) {
        viewModelScope.launch {
            cachingService
                .cacheBook(book, mediaProvider.providePreferredChannel())
                .collect { _bookCachingProgress[book.id]?.value = it }
        }
    }

    fun getCacheProgress(bookId: String) = _bookCachingProgress
        .getOrPut(bookId) {
            MutableStateFlow(CacheProgress.Idle)
        }

    fun toggleCacheForce() {
        when (localCacheUsing()) {
            true -> preferences.disableForceCache()
            false -> preferences.enableForceCache()
        }
    }

    fun localCacheUsing() = preferences.isForceCache()
}
