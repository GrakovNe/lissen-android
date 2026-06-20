package org.grakovne.lissen.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DetailedItem.Companion.same
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.DefaultTimerActivator
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition
import org.grakovne.lissen.playback.service.calculateChapterPosition
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MediaRepository
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val mediaChannel: LissenMediaProvider,
    private val eventBus: PlaybackEventBus,
    private val defaultTimerActivator: DefaultTimerActivator,
  ) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mediaController: MediaController

    private val token =
      SessionToken(
        context,
        ComponentName(context, PlaybackService::class.java),
      )

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _timerOption = MutableStateFlow<TimerOption?>(null)
    val timerOption: StateFlow<TimerOption?> = _timerOption.asStateFlow()

    private val _timerRemaining = MutableStateFlow<Long?>(null)
    val timerRemaining: StateFlow<Long?> = _timerRemaining.asStateFlow()

    private val _playAfterPrepare = MutableStateFlow(false)
    private val _isPlaybackReady = MutableStateFlow(false)
    val isPlaybackReady: StateFlow<Boolean> = _isPlaybackReady.asStateFlow()

    private val _totalPosition = MutableStateFlow(0.0)
    val totalPosition: StateFlow<Double> = _totalPosition.asStateFlow()

    private val _playingBook = MutableStateFlow<DetailedItem?>(null)
    val playingBook: StateFlow<DetailedItem?> = _playingBook.asStateFlow()

    private val _mediaPreparingError = MutableStateFlow(false)
    val mediaPreparingError: StateFlow<Boolean> = _mediaPreparingError.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(preferences.getPlaybackSpeed())
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _currentChapterPosition = MutableStateFlow(0.0)
    val currentChapterPosition: StateFlow<Double> = _currentChapterPosition.asStateFlow()

    private val _currentChapterDuration = MutableStateFlow(0.0)
    val currentChapterDuration: StateFlow<Double> = _currentChapterDuration.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    init {
      val controllerBuilder = MediaController.Builder(context, token)
      val futureController = controllerBuilder.buildAsync()

      Futures.addCallback(
        futureController,
        object : FutureCallback<MediaController> {
          override fun onSuccess(controller: MediaController) {
            mediaController = controller

            scope.launch {
              eventBus.events.collect { event ->
                when (event) {
                  is PlaybackEvent.PlaybackReady -> {
                    val book = preferences.getPlayingItem()
                    book?.let {
                      updateProgress(book).await()
                      _isPlaybackReady.value = true

                      if (_playAfterPrepare.value) {
                        _playAfterPrepare.value = false
                        play()
                      }
                    }
                  }

                  is PlaybackEvent.TimerExpired -> {
                    defaultTimerActivator.onTimerExpired()
                    _timerOption.value = null
                    pause()
                  }

                  is PlaybackEvent.TimerTick -> {
                    _timerRemaining.value = event.remainingSeconds
                  }
                }
              }
            }

            mediaController.addListener(
              object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                  _isPlaying.value = isPlaying
                  if (isPlaying) {
                    defaultTimerActivator.onPlaybackStarted { updateTimer(it) }
                    _playingBook.value?.let { startUpdatingProgress(it) }
                  } else {
                    stopUpdatingProgress()
                  }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                  if (playbackState == Player.STATE_ENDED) {
                    mediaController.seekTo(0, 0)
                    mediaController.pause()
                  }
                }

                override fun onPlayerError(error: PlaybackException) {
                  Timber.e(error, "Playback error: ${error.errorCodeName}")
                  _isPlaying.value = false
                  _mediaPreparingError.value = true
                }
              },
            )
          }

          override fun onFailure(t: Throwable) {
            Timber.e("Unable to add callback to player")
          }
        },
        MoreExecutors.directExecutor(),
      )
    }

    fun updateTimer(
      timerOption: TimerOption?,
      position: Double? = null,
    ) {
      defaultTimerActivator.onTimerManuallySet()
      _timerOption.value = timerOption

      when (timerOption) {
        is DurationTimerOption -> {
          scheduleServiceTimer(timerOption.duration * 60.0, timerOption)
        }

        is CurrentEpisodeTimerOption -> {
          val playingBook = playingBook.value ?: return
          val currentPosition = position ?: totalPosition.value

          val (chapterIndex, chapterPosition) = calculateChapterIndexAndPosition(playingBook, currentPosition)
          val chapterDuration =
            chapterIndex
              .takeIf { it in playingBook.chapters.indices }
              ?.let { playingBook.chapters[it].duration }
              ?: return

          scheduleServiceTimer(
            delay = (chapterDuration - chapterPosition) / preferences.getPlaybackSpeed(),
            option = timerOption,
          )
        }

        null -> {
          cancelServiceTimer()
        }
      }
    }

    fun rewind() {
      seekTo(totalPosition.value - getSeekTime(preferences.getSeekTime().rewind))
    }

    fun forward() {
      seekTo(totalPosition.value + getSeekTime(preferences.getSeekTime().forward))
    }

    fun setChapter(index: Int) {
      val book = playingBook.value ?: return
      try {
        val chapterStartsAt =
          book
            .chapters[index]
            .start

        seekTo(chapterStartsAt)
      } catch (ex: Exception) {
        Timber.w("Unable to set chapter index=$index for ${book.id} due to: ${ex.message}")
        return
      }
    }

    fun clearPlayingBook() {
      Timber.d("Clearing playing book: ${_playingBook.value?.id}")
      pause()

      _playingBook.value = null
      preferences.clearPlayingItem()
    }

    fun setTotalPosition(totalPosition: Double) {
      seekTo(totalPosition)
    }

    fun setChapterPosition(chapterPosition: Double) {
      val book = playingBook.value ?: return
      val overallPosition = totalPosition.value

      val currentIndex = calculateChapterIndex(book, overallPosition)

      if (currentIndex < 0) {
        return
      }

      try {
        val absolutePosition =
          currentIndex
            .let { chapterIndex -> book.chapters[chapterIndex].start }
            .let { it + chapterPosition }

        seekTo(absolutePosition)
      } catch (ex: Exception) {
        Timber.w("Unable to set chapter position=${chapterPosition.toInt()}s for ${book.id} due to: ${ex.message}")
        return
      }
    }

    fun prepareAndPlay(book: DetailedItem) {
      Timber.d("prepareAndPlay: bookId=${book.id}, alreadyReady=${isPlaybackReady.value}")
      when (isPlaybackReady.value) {
        true -> {
          play()
        }

        else -> {
          _playAfterPrepare.value = true
          startPreparingPlayback(book)
        }
      }
    }

    fun togglePlayPause() {
      if (currentChapterIndex.value == -1) {
        Timber.w("Tried to toggle play/pause in the empty book. Skipping")
        return
      }

      when (isPlaying.value) {
        true -> pause()
        else -> play()
      }
    }

    fun setPlaybackSpeed(factor: Float) {
      Timber.d("Setting playback speed to $factor")
      val speed =
        when {
          factor < 0.5f -> 0.5f
          factor > 3f -> 3f
          else -> factor
        }

      if (::mediaController.isInitialized) {
        mediaController.setPlaybackSpeed(speed)
      }

      _playbackSpeed.value = speed
      preferences.savePlaybackSpeed(speed)

      adjustTimer(totalPosition.value)
    }

    suspend fun preparePlayback(bookId: String) {
      coroutineScope {
        withContext(Dispatchers.IO) {
          mediaChannel
            .fetchBook(bookId)
            .foldAsync(
              onSuccess = { startPreparingPlayback(it) },
              onFailure = { _mediaPreparingError.value = true },
            )
        }
      }
    }

    fun nextTrack() {
      val book = playingBook.value ?: return
      val overallPosition = totalPosition.value
      val currentIndex = calculateChapterIndex(book, overallPosition)
      Timber.d("Next track: bookId=${book.id}, currentChapter=$currentIndex -> ${currentIndex + 1}")

      val nextChapterIndex = currentIndex + 1
      setChapter(nextChapterIndex)
    }

    fun previousTrack(rewindRequired: Boolean = true) {
      val book = playingBook.value ?: return
      val overallPosition = totalPosition.value

      val (currentIndex, chapterPosition) = calculateChapterIndexAndPosition(book, overallPosition)
      Timber.d("Previous track: bookId=${book.id}, currentChapter=$currentIndex, chapterPosition=${chapterPosition.toInt()}s")

      val currentIndexReplay = (chapterPosition > CURRENT_TRACK_REPLAY_THRESHOLD || currentIndex == 0)

      when {
        currentIndexReplay && rewindRequired -> setChapter(currentIndex)
        currentIndex > 0 -> setChapter(currentIndex - 1)
      }
    }

    private fun scheduleServiceTimer(
      delay: Double,
      option: TimerOption,
    ) {
      eventBus.send(PlaybackCommand.SetTimer(delay, option))
    }

    private fun cancelServiceTimer() {
      eventBus.send(PlaybackCommand.CancelTimer)
    }

    private fun startUpdatingProgress(detailedItem: DetailedItem) {
      handler.removeCallbacksAndMessages(null)

      handler.postDelayed(
        object : Runnable {
          override fun run() {
            updateProgress(detailedItem)
            handler.postDelayed(this, 500)
          }
        },
        500,
      )
    }

    private fun stopUpdatingProgress() {
      handler.removeCallbacksAndMessages(null)
    }

    fun clearPreparedItem() {
      if (timerOption.value != null) {
        _timerOption.value = null
        cancelServiceTimer()
      }

      defaultTimerActivator.onNewBookPrepared()
      _mediaPreparingError.value = false
      _isPlaybackReady.value = false
    }

    private fun startPreparingPlayback(book: DetailedItem) {
      val sameBook = _playingBook.value?.same(book) ?: false

      if (sameBook.not()) {
        _totalPosition.value = 0.0
        _isPlaying.value = false

        _playingBook.value = book
        preferences.savePlayingItem(book)

        val intent = Intent(context, PlaybackService::class.java)
        when (inBackground()) {
          true -> context.startForegroundService(intent)
          false -> context.startService(intent)
        }

        eventBus.send(PlaybackCommand.PreparePlayback)
      }
    }

    private fun updateProgress(detailedItem: DetailedItem): Deferred<Unit> =
      scope.async {
        val currentIndex = mediaController.currentMediaItemIndex
        val accumulated = detailedItem.chapters.take(currentIndex).sumOf { it.duration }
        val currentFilePosition = mediaController.currentPosition / 1000.0

        val newPosition = accumulated + currentFilePosition
        _totalPosition.value = newPosition
        updateCurrentTrackData()
      }

    private fun play() {
      context.startForegroundService(Intent(context, PlaybackService::class.java))
      eventBus.send(PlaybackCommand.Play)
    }

    private fun pause() {
      eventBus.send(PlaybackCommand.Pause)
    }

    private fun seekTo(position: Double) {
      val book = playingBook.value ?: return

      if (book.chapters.isEmpty()) {
        Timber.d("Tried to seek on the empty book")
        return
      }

      val overallDuration =
        book
          .chapters
          .sumOf { it.duration }

      val current = totalPosition.value

      val direction =
        when (current > maxOf(0.0, position)) {
          true -> ScrollingDirection.BACKWARD
          false -> ScrollingDirection.FORWARD
        }

      var safePosition = minOf(overallDuration, maxOf(0.0, position))

      while (book.chapters[calculateChapterIndex(book, safePosition)].available.not()) {
        val chapterIndex =
          when (direction) {
            ScrollingDirection.FORWARD -> calculateChapterIndex(book, safePosition) + 1
            ScrollingDirection.BACKWARD -> calculateChapterIndex(book, safePosition) - 1
          }

        safePosition =
          when {
            chapterIndex in 0..book.chapters.lastIndex -> book.chapters[chapterIndex].start
            else -> break
          }
      }

      eventBus.send(PlaybackCommand.SeekTo(safePosition))
      adjustTimer(safePosition)
    }

    private fun adjustTimer(position: Double) {
      when (_timerOption.value) {
        is CurrentEpisodeTimerOption -> {
          updateTimer(
            timerOption = _timerOption.value,
            position = position,
          )
        }

        is DurationTimerOption -> {}

        null -> {}
      }
    }

    private fun updateCurrentTrackData() {
      val book = playingBook.value ?: return
      val totalPosition = totalPosition.value

      val trackIndex = calculateChapterIndex(book, totalPosition)
      val trackPosition = calculateChapterPosition(book, totalPosition)

      _currentChapterIndex.value = trackIndex
      _currentChapterPosition.value = trackPosition
      _currentChapterDuration.value =
        book
          .chapters
          .getOrNull(trackIndex)
          ?.duration
          ?: 0.0
    }

    suspend fun createBookmark(title: String? = null) {
      Timber.d("Creating bookmark for ${_playingBook.value?.id} at position=${_totalPosition.value.toInt()}s")
      val playingBook = _playingBook.value ?: return
      val totalPosition = _totalPosition.value

      val currentChapter = playingBook.chapters[calculateChapterIndex(playingBook, totalPosition)].title
      val chapterPosition = _currentChapterPosition.value

      val bookmarkTitle =
        when (title) {
          null -> buildBookmarkTitle(currentChapter, chapterPosition)
          else -> title
        }

      mediaChannel
        .createBookmark(
          libraryItemId = playingBook.id,
          totalPosition = totalPosition,
          title = bookmarkTitle,
        )

      _bookmarks.value = mediaChannel.provideBookmarks(playingBook.id)
    }

    suspend fun dropBookmark(bookmark: Bookmark) {
      Timber.d("Dropping bookmark for ${bookmark.libraryItemId} at position=${bookmark.totalPosition.toInt()}s")
      mediaChannel.dropBookmark(bookmark = bookmark)

      _bookmarks.value = mediaChannel.provideBookmarks(bookmark.libraryItemId)
    }

    suspend fun updateBookmarks() {
      val book = playingBook.value ?: return
      val bookmarks = withContext(Dispatchers.IO) { mediaChannel.updateAndProvideBookmarks(book.id) }

      _bookmarks.value = bookmarks
    }

    private companion object {
      private const val CURRENT_TRACK_REPLAY_THRESHOLD = 5

      private fun getSeekTime(seconds: Int?): Long = seconds?.toLong() ?: 30L

      private fun inBackground(): Boolean =
        ProcessLifecycleOwner
          .get()
          .lifecycle
          .currentState
          .isAtMost(Lifecycle.State.STARTED)

      private fun Lifecycle.State.isAtMost(state: Lifecycle.State) = this <= state
    }
  }

enum class ScrollingDirection {
  FORWARD,
  BACKWARD,
}
