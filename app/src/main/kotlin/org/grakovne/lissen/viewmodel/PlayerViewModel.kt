package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class PlayerViewModel
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val preferences: PlaybackPreferences,
  ) : ViewModel() {
    val book: StateFlow<DetailedItem?> = mediaRepository.playingBook

    val currentChapterIndex: StateFlow<Int> = mediaRepository.currentChapterIndex
    val currentChapterPosition: StateFlow<Double> = mediaRepository.currentChapterPosition

    val currentChapterDuration: StateFlow<Double> = mediaRepository.currentChapterDuration
    val totalPosition: StateFlow<Double> = mediaRepository.totalPosition

    val timerOption: StateFlow<TimerOption?> = mediaRepository.timerOption
    val timerRemaining: StateFlow<Long?> = mediaRepository.timerRemaining

    private val _playingQueueExpanded = MutableStateFlow(false)
    val playingQueueExpanded: StateFlow<Boolean> = _playingQueueExpanded.asStateFlow()

    val isPlaybackReady: StateFlow<Boolean> = mediaRepository.isPlaybackReady
    val playbackSpeed: StateFlow<Float> = mediaRepository.playbackSpeed
    val preparingError: StateFlow<Boolean> = mediaRepository.mediaPreparingError

    private val _searchRequested = MutableStateFlow(false)
    val searchRequested: StateFlow<Boolean> = _searchRequested.asStateFlow()

    private val _searchToken = MutableStateFlow(EMPTY_SEARCH)
    val searchToken: StateFlow<String> = _searchToken.asStateFlow()

    val isPlaying: StateFlow<Boolean> = mediaRepository.isPlaying

    val bookmarks: StateFlow<List<Bookmark>> = mediaRepository.bookmarks

    fun createBookmark(title: String? = null) {
      Timber.d("User action: createBookmark at position=${totalPosition.value.toInt()}s")
      viewModelScope.launch {
        mediaRepository.createBookmark(title)
      }
    }

    fun dropBookmark(bookmark: Bookmark) {
      Timber.d("User action: dropBookmark at position=${bookmark.totalPosition.toInt()}s")
      viewModelScope.launch {
        mediaRepository.dropBookmark(bookmark = bookmark)
      }
    }

    fun updateBookmarks() {
      viewModelScope.launch { mediaRepository.updateBookmarks() }
    }

    fun updatePlayingItem() {
      val playingItem = preferences.getPlayingItem()

      when (playingItem?.id) {
        null -> viewModelScope.launch { mediaRepository.clearPlayingBook() }
        else -> viewModelScope.launch { mediaRepository.preparePlayback(playingItem.id) }
      }
    }

    fun expandPlayingQueue() {
      _playingQueueExpanded.value = true
    }

    fun setTimer(option: TimerOption?) {
      Timber.d("User action: setTimer option=$option")
      mediaRepository.updateTimer(option)
    }

    fun collapsePlayingQueue() {
      _playingQueueExpanded.value = false
    }

    fun togglePlayingQueue() {
      _playingQueueExpanded.value = !_playingQueueExpanded.value
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

    fun clearPrepared() {
      mediaRepository.clearPreparedItem()
    }

    fun preparePlayback(bookId: String) {
      viewModelScope.launch {
        mediaRepository.clearPreparedItem()
        mediaRepository.preparePlayback(bookId)
      }
    }

    fun rewind() {
      Timber.d("User action: rewind at position=${totalPosition.value.toInt()}s")
      mediaRepository.rewind()
    }

    fun forward() {
      Timber.d("User action: forward at position=${totalPosition.value.toInt()}s")
      mediaRepository.forward()
    }

    fun seekTo(chapterPosition: Double) {
      Timber.d("User action: seekTo chapterPosition=${chapterPosition.toInt()}s")
      mediaRepository.setChapterPosition(chapterPosition)
    }

    fun setTotalPosition(totalPosition: Double) {
      Timber.d("User action: setTotalPosition ${totalPosition.toInt()}s")
      mediaRepository.setTotalPosition(totalPosition)
    }

    fun setChapter(chapter: PlayingChapter) {
      if (chapter.available) {
        val index = book.value?.chapters?.indexOf(chapter) ?: -1
        Timber.d("User action: setChapter '${chapter.title}' index=$index")
        mediaRepository.setChapter(index)
      }
    }

    fun clearPlayingBook() {
      Timber.d("User action: clearPlayingBook bookId=${book.value?.id}")
      mediaRepository.clearPlayingBook()
    }

    fun setPlaybackSpeed(factor: Float) {
      Timber.d("User action: setPlaybackSpeed $factor")
      mediaRepository.setPlaybackSpeed(factor)
    }

    fun nextTrack() {
      Timber.d("User action: nextTrack")
      mediaRepository.nextTrack()
    }

    fun previousTrack() {
      Timber.d("User action: previousTrack")
      mediaRepository.previousTrack()
    }

    fun togglePlayPause() {
      Timber.d("User action: togglePlayPause (isPlaying=${isPlaying.value})")
      mediaRepository.togglePlayPause()
    }

    fun prepareAndPlay() {
      val playingBook = preferences.getPlayingItem() ?: return
      mediaRepository.prepareAndPlay(playingBook)
    }

    companion object {
      private const val EMPTY_SEARCH = ""
    }
  }
