package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.BookChapter
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.playback.service.calculateChapterPosition
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaChannel: LissenMediaProvider,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val book: LiveData<DetailedItem> = mediaRepository.playingBook

    private val mediaItemPosition: LiveData<Double> = mediaRepository.mediaItemPosition

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

    private val _currentChapterIndex = MediatorLiveData<Int>().apply {
        addSource(mediaItemPosition) { updateCurrentTrackData() }
        addSource(book) { updateCurrentTrackData() }
    }
    val currentChapterIndex: LiveData<Int> = _currentChapterIndex

    private val _currentChapterPosition = MediatorLiveData<Double>().apply {
        addSource(mediaItemPosition) { updateCurrentTrackData() }
        addSource(book) { updateCurrentTrackData() }
    }
    val currentChapterPosition: LiveData<Double> = _currentChapterPosition

    private val _currentChapterDuration = MediatorLiveData<Double>().apply {
        addSource(mediaItemPosition) { updateCurrentTrackData() }
        addSource(book) { updateCurrentTrackData() }
    }

    val currentChapterDuration: LiveData<Double> = _currentChapterDuration

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
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                mediaRepository.mediaPreparing()
                mediaChannel.fetchBook(bookId)
            }

            result.foldAsync(
                onSuccess = {
                    withContext(Dispatchers.IO) {
                        mediaRepository.startPreparingPlayback(it)
                    }
                },
                onFailure = {
                }
            )
        }
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

    private fun updateCurrentTrackData() {
        val book = book.value ?: return
        val totalPosition = mediaRepository.mediaItemPosition.value ?: return

        val trackIndex = calculateChapterIndex(book, totalPosition)
        val trackPosition = calculateChapterPosition(book, totalPosition)

        _currentChapterIndex.value = trackIndex
        _currentChapterPosition.value = trackPosition
        _currentChapterDuration.value = book
            .chapters
            .getOrNull(trackIndex)
            ?.duration
            ?: 0.0
    }

    companion object {
        private const val EMPTY_SEARCH = ""
    }
}
