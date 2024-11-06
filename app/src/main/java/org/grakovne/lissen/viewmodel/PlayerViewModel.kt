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
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.playback.MediaRepository
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaChannel: LissenMediaProvider,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val book: LiveData<DetailedBook> = mediaRepository.playingBook

    private val mediaItemPosition: LiveData<Double> = mediaRepository.mediaItemPosition

    private val _playingQueueExpanded = MutableLiveData(false)
    val playingQueueExpanded: LiveData<Boolean> = _playingQueueExpanded

    val isPlaybackReady: LiveData<Boolean> = mediaRepository.isPlaybackReady
    val playbackSpeed: LiveData<Float> = mediaRepository.playbackSpeed

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

    fun collapsePlayingQueue() {
        _playingQueueExpanded.value = false
    }

    fun togglePlayingQueue() {
        _playingQueueExpanded.value = !(_playingQueueExpanded.value ?: false)
    }

    private fun updateCurrentTrackData() {
        val book = book.value ?: return
        val position = mediaRepository.mediaItemPosition.value ?: return

        val trackIndex = calculateChapterIndex(position)
        val trackPosition = calculateChapterPosition(position)

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
                        mediaRepository.startPreparingPlayingBook(it)
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
        val absolutePosition = currentChapterIndex.value
            ?.let { chapterIndex -> book.value?.chapters?.get(chapterIndex)?.start }
            ?.let { it + chapterPosition } ?: return

        mediaRepository.seekTo(absolutePosition)
    }

    fun setChapter(index: Int) {
        val chapterStartsAt = book
            .value
            ?.chapters
            ?.get(index)
            ?.start
            ?: 0.0

        mediaRepository.seekTo(chapterStartsAt)
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

    private fun calculateChapterIndex(position: Double): Int {
        val currentBook = book.value ?: return 0
        var accumulatedDuration = 0.0

        for ((index, chapter) in currentBook.chapters.withIndex()) {
            accumulatedDuration += chapter.duration
            if (position < accumulatedDuration - 0.1) {
                return index
            }
        }

        return currentBook.chapters.size - 1
    }

    private fun calculateChapterPosition(overallPosition: Double): Double {
        val currentBook = book.value ?: return 0.0
        var accumulatedDuration = 0.0

        for (chapter in currentBook.chapters) {
            val chapterEnd = accumulatedDuration + chapter.duration
            if (overallPosition < chapterEnd - 0.1) {
                return (overallPosition - accumulatedDuration)
            }
            accumulatedDuration = chapterEnd
        }

        return 0.0
    }

    companion object {

        private const val CURRENT_TRACK_REPLAY_THRESHOLD = 5
    }
}
