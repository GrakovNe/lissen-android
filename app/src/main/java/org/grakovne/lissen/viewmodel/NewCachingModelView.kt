package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.NewBookCachingService
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject


@HiltViewModel
class NewCachingModelView @Inject constructor(
    private val cachingService: NewBookCachingService,
    private val preferences: LissenSharedPreferences,
    private val mediaProvider: LissenMediaProvider,
) : ViewModel() {

    private val _bookCachingProgress = mutableMapOf<String, MutableStateFlow<CacheProgress>>()

    fun requestCache(
        bookId: String,
        currentPosition: Double,
        option: DownloadOption
    ) {

        viewModelScope.launch {
            cachingService
                .cacheBook(
                    bookId = bookId,
                    option = option,
                    channel = mediaProvider.providePreferredChannel(),
                    currentTotalPosition = currentPosition
                )
                .collect { _bookCachingProgress[bookId]?.value = it }
        }

    }

    fun dropCache(bookId: String) {

    }

    fun isPlayingBookCached(): Boolean = true


}