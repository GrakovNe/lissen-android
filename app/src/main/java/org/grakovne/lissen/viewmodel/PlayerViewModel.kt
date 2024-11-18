package org.grakovne.lissen.viewmodel

import android.util.Log
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
        val currentPosition = _currentChapterPosition.value ?: 0.0
        seekTo(maxOf(0.0, currentPosition - 10L))
    }

    fun forward() {
        val currentPosition = _currentChapterPosition.value ?: 0.0
        val currentDuration = _currentChapterDuration.value ?: 0.0
        seekTo(minOf(currentDuration, currentPosition + 30L))
    }

    fun seekTo(chapterPosition: Double) {
        val currentIndex = currentChapterIndex.value ?: return

        if (currentIndex < 0) {
            Log.w(TAG, "Unable seek to $chapterPosition because there is no chapter")
            return
        }

        val absolutePosition = currentIndex
            .let { chapterIndex -> book.value?.chapters?.get(chapterIndex)?.start }
            ?.let { it + chapterPosition }
            ?: return

        mediaRepository.seekTo(absolutePosition)
    }

    fun setChapter(chapter: BookChapter) {
        val index = book.value?.chapters?.indexOf(chapter) ?: -1
        setChapter(index)
    }

    fun setChapter(index: Int) {
        try {
            val chapterStartsAt = book
                .value
                ?.chapters
                ?.get(index)
                ?.start
                ?: 0.0

            mediaRepository.seekTo(chapterStartsAt)
        } catch (ex: Exception) {
            Log.e(TAG, "Tried to play $index element on $book state, but index is not exist")
            return
        }
    }

    fun setPlaybackSpeed(factor: Float) = mediaRepository.setPlaybackSpeed(factor)

    fun nextTrack() {
        val nextChapterIndex = currentChapterIndex.value?.let { it + 1 } ?: return
        setChapter(nextChapterIndex)
    }

    fun previousTrack() {
        val position = currentChapterPosition.value ?: return
        val index = currentChapterIndex.value ?: return

        val currentIndexReplay = (position > CURRENT_TRACK_REPLAY_THRESHOLD || index == 0)

        when {
            currentIndexReplay -> setChapter(index)
            index > 0 -> setChapter(index - 1)
        }
    }

    fun togglePlayPause() {
        when (isPlaying.value) {
            true -> pause()
            else -> play()
        }
    }

    private fun play() {
        mediaRepository.play()
    }

    private fun pause() {
        mediaRepository.pauseAudio()
    }

    companion object {

        private const val EMPTY_SEARCH = ""
        private const val TAG = "PlayerViewModel"
        private const val CURRENT_TRACK_REPLAY_THRESHOLD = 5
    }
}
