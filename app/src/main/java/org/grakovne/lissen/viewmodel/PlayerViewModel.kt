package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.domain.BookChapter
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.widget.MediaRepository
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    val book: LiveData<DetailedItem> = mediaRepository.playingBook

    val currentChapterIndex: LiveData<Int> = mediaRepository.currentChapterIndex
    val currentChapterPosition: LiveData<Double> = mediaRepository.currentChapterPosition
    val currentChapterDuration: LiveData<Double> = mediaRepository.currentChapterDuration

    val timerOption: LiveData<TimerOption?> = mediaRepository.timerOption

    private val _playingQueueExpanded = MutableLiveData(false)
    val playingQueueExpanded: LiveData<Boolean> = _playingQueueExpanded

    val isPlaybackReady: LiveData<Boolean> = mediaRepository.isPlaybackReady
    val playbackSpeed: LiveData<Float> = mediaRepository.playbackSpeed

    private val _searchRequested = MutableLiveData(false)
    val searchRequested: LiveData<Boolean> = _searchRequested

    private val _searchToken = MutableLiveData(EMPTY_SEARCH)
    val searchToken: LiveData<String> = _searchToken

    val isPlaying: LiveData<Boolean> = mediaRepository.isPlaying

    fun expandPlayingQueue() {
        _playingQueueExpanded.value = true
    }

    fun setTimer(option: TimerOption?) {
        mediaRepository.updateTimer(option)
    }

    fun collapsePlayingQueue() {
        _playingQueueExpanded.value = false
    }

    fun togglePlayingQueue() {
        _playingQueueExpanded.value = !(_playingQueueExpanded.value ?: false)
    }

    fun requestSearch() {
        _searchRequested.value = true
    }

    fun dismissSearch() {
        _searchRequested.value = false
        _searchToken.value = EMPTY_SEARCH
    }

    fun updateSearch(token: String) {
        _searchToken.value = token
    }

    fun preparePlayback(bookId: String) {
        viewModelScope.launch { mediaRepository.preparePlayback(bookId) }
    }

    fun rewind() {
        mediaRepository.rewind()
    }

    fun forward() {
        mediaRepository.forward()
    }

    fun seekTo(chapterPosition: Double) {
        mediaRepository.setChapterPosition(chapterPosition)
    }

    fun setChapter(chapter: BookChapter) {
        val index = book.value?.chapters?.indexOf(chapter) ?: -1
        mediaRepository.setChapter(index)
    }

    fun setPlaybackSpeed(factor: Float) = mediaRepository.setPlaybackSpeed(factor)

    fun nextTrack() = mediaRepository.nextTrack()

    fun previousTrack() = mediaRepository.previousTrack()

    fun togglePlayPause() = mediaRepository.togglePlayPause()

    companion object {

        private const val EMPTY_SEARCH = ""
    }
}
