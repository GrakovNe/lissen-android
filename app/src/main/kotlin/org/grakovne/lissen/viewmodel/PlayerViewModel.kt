package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class PlayerViewModel
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val preferences: LissenSharedPreferences,
  ) : ViewModel() {
    val book: LiveData<DetailedItem?> = mediaRepository.playingBook

    val currentChapterIndex: LiveData<Int> = mediaRepository.currentChapterIndex
    val currentChapterPosition: LiveData<Double> = mediaRepository.currentChapterPosition

    val currentChapterDuration: LiveData<Double> = mediaRepository.currentChapterDuration
    val totalPosition: LiveData<Double> = mediaRepository.totalPosition

    val timerOption: LiveData<TimerOption?> = mediaRepository.timerOption
    val timerRemaining: LiveData<Long?> = mediaRepository.timerRemaining

    private val _playingQueueExpanded = MutableLiveData(false)
    val playingQueueExpanded: LiveData<Boolean> = _playingQueueExpanded

    val isPlaybackReady: LiveData<Boolean> = mediaRepository.isPlaybackReady
    val playbackSpeed: LiveData<Float> = mediaRepository.playbackSpeed
    val preparingError: LiveData<Boolean> = mediaRepository.mediaPreparingError

    private val _searchRequested = MutableLiveData(false)
    val searchRequested: LiveData<Boolean> = _searchRequested

    private val _searchToken = MutableLiveData(EMPTY_SEARCH)
    val searchToken: LiveData<String> = _searchToken

    val isPlaying: LiveData<Boolean> = mediaRepository.isPlaying

    fun recoverMiniPlayer() {
      val playingBook = preferences.getPlayingBook()

      if (playingBook?.id != null && book.value == null) {
        viewModelScope.launch {
          mediaRepository.preparePlayback(playingBook.id)
        }
      }
    }

    fun expandPlayingQueue() {
      _playingQueueExpanded.postValue(true)
    }

    fun setTimer(option: TimerOption?) {
      mediaRepository.updateTimer(option)
    }

    fun collapsePlayingQueue() {
      _playingQueueExpanded.postValue(false)
    }

    fun togglePlayingQueue() {
      _playingQueueExpanded.postValue(!(_playingQueueExpanded.value ?: false))
    }

    fun requestSearch() {
      _searchRequested.postValue(true)
    }

    fun dismissSearch() {
      _searchRequested.postValue(false)
      _searchToken.postValue(EMPTY_SEARCH)
    }

    fun updateSearch(token: String) {
      _searchToken.postValue(token)
    }

    fun preparePlayback(bookId: String) {
      viewModelScope.launch {
        mediaRepository.clearPreparedItem()
        mediaRepository.preparePlayback(bookId)
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

    fun setChapter(chapter: PlayingChapter) {
      if (chapter.available) {
        val index = book.value?.chapters?.indexOf(chapter) ?: -1
        mediaRepository.setChapter(index)
      }
    }

    fun clearPlayingBook() = mediaRepository.clearPlayingBook()

    fun setPlaybackSpeed(factor: Float) = mediaRepository.setPlaybackSpeed(factor)

    fun nextTrack() = mediaRepository.nextTrack()

    fun previousTrack() = mediaRepository.previousTrack()

    fun togglePlayPause() = mediaRepository.togglePlayPause()

    fun prepareAndPlay() {
      val playingBook = preferences.getPlayingBook() ?: return
      mediaRepository.prepareAndPlay(playingBook)
    }

    companion object {
      private const val EMPTY_SEARCH = ""
    }
  }
