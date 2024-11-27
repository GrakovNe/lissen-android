package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.BookCachingService
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject


@HiltViewModel
class NewCachingModelView @Inject constructor(
    private val cachingService: BookCachingService,
    private val preferences: LissenSharedPreferences,
    private val mediaProvider: LissenMediaProvider,
) : ViewModel() {

    fun requestCache(option: DownloadOption) {

    }

    fun dropCache() {

    }

    fun isPlayingBookCached(): Boolean = true


}