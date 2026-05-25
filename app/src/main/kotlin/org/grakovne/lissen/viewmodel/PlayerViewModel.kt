package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.lib.domain.Bookmark
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import timber.log.Timber
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

    val bookmarks = mediaRepository.bookmarks

    fun createBookmark(title: String? = null) {
      Timber.d("User action: createBookmark at position=${totalPosition.value?.toInt()}s")
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
      _playingQueueExpanded.postValue(true)
    }

    fun setTimer(option: TimerOption?) {
      Timber.d("User action: setTimer option=$option")
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
      Timber.d("User action: rewind at position=${totalPosition.value?.toInt()}s")
      mediaRepository.rewind()
    }

    fun forward() {
      Timber.d("User action: forward at position=${totalPosition.value?.toInt()}s")
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
