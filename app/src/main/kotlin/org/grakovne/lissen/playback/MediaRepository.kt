package org.grakovne.lissen.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.SeekTimeOption
import org.grakovne.lissen.lib.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.PlaybackService.Companion.ACTION_SEEK_TO
import org.grakovne.lissen.playback.service.PlaybackService.Companion.PLAYBACK_READY
import org.grakovne.lissen.playback.service.PlaybackService.Companion.POSITION
import org.grakovne.lissen.playback.service.PlaybackService.Companion.TIMER_EXPIRED
import org.grakovne.lissen.playback.service.PlaybackService.Companion.TIMER_OPTION_EXTRA
import org.grakovne.lissen.playback.service.PlaybackService.Companion.TIMER_REMAINING
import org.grakovne.lissen.playback.service.PlaybackService.Companion.TIMER_TICK
import org.grakovne.lissen.playback.service.PlaybackService.Companion.TIMER_VALUE_EXTRA
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.playback.service.calculateChapterPosition
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MediaRepository
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val mediaChannel: LissenMediaProvider,
  ) {
    private lateinit var mediaController: MediaController

    private val token =
      SessionToken(
        context,
        ComponentName(context, PlaybackService::class.java),
      )

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _timerOption = MutableLiveData<TimerOption?>()
    val timerOption = _timerOption

    private val _timerRemaining = MutableLiveData<Long>()
    val timerRemaining = _timerRemaining

    private val _playAfterPrepare = MutableLiveData(false)
    private val _isPlaybackReady = MutableLiveData(false)
    val isPlaybackReady: LiveData<Boolean> = _isPlaybackReady

    private val _totalPosition = MutableLiveData<Double>()
    val totalPosition: LiveData<Double> = _totalPosition

    private val _playingBook = MutableLiveData<DetailedItem?>()
    val playingBook: LiveData<DetailedItem?> = _playingBook

    private val _mediaPreparingError = MutableLiveData<Boolean>()
    val mediaPreparingError: LiveData<Boolean> = _mediaPreparingError

    private val _playbackSpeed = MutableLiveData(preferences.getPlaybackSpeed())
    val playbackSpeed: LiveData<Float> = _playbackSpeed

    private val _currentChapterIndex =
      MediatorLiveData<Int>().apply {
        addSource(totalPosition) { updateCurrentTrackData() }
        addSource(playingBook) { updateCurrentTrackData() }
      }

    val currentChapterIndex: LiveData<Int> = _currentChapterIndex

    private val _currentChapterPosition =
      MediatorLiveData<Double>().apply {
        addSource(totalPosition) { updateCurrentTrackData() }
        addSource(playingBook) { updateCurrentTrackData() }
      }

    val currentChapterPosition: LiveData<Double> = _currentChapterPosition

    private val _currentChapterDuration =
      MediatorLiveData<Double>().apply {
        addSource(totalPosition) { updateCurrentTrackData() }
        addSource(playingBook) { updateCurrentTrackData() }
      }

    val currentChapterDuration: LiveData<Double> = _currentChapterDuration

    private val handler = Handler(Looper.getMainLooper())

    init {
      val controllerBuilder = MediaController.Builder(context, token)
      val futureController = controllerBuilder.buildAsync()

      Futures.addCallback(
        futureController,
        object : FutureCallback<MediaController> {
          override fun onSuccess(controller: MediaController) {
            mediaController = controller

            LocalBroadcastManager
              .getInstance(context)
              .registerReceiver(playbackReadyReceiver, IntentFilter(PLAYBACK_READY))

            LocalBroadcastManager
              .getInstance(context)
              .registerReceiver(timerExpiredReceiver, IntentFilter(TIMER_EXPIRED))

            LocalBroadcastManager
              .getInstance(context)
              .registerReceiver(timerTickReceiver, IntentFilter(TIMER_TICK))

            mediaController.addListener(
              object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                  _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                  if (playbackState == Player.STATE_ENDED) {
                    mediaController.seekTo(0, 0)
                    mediaController.pause()
                  }
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

    private val playbackReadyReceiver =
      object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(
          context: Context?,
          intent: Intent?,
        ) {
          if (intent?.action == PLAYBACK_READY) {
            val book = preferences.getPlayingBook()

            book?.let {
              CoroutineScope(Dispatchers.Main).launch {
                updateProgress(book).await()
                startUpdatingProgress(book)
                _isPlaybackReady.postValue(true)

                if (_playAfterPrepare.value == true) {
                  _playAfterPrepare.postValue(false)
                  play()
                }
              }
            }
          }
        }
      }

    private val timerExpiredReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context?,
          intent: Intent?,
        ) {
          if (intent?.action == TIMER_EXPIRED) {
            _timerOption.postValue(null)
            pause()
          }
        }
      }

    private val timerTickReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context?,
          intent: Intent?,
        ) {
          if (intent?.action == TIMER_TICK) {
            val remaining = intent.getLongExtra(TIMER_REMAINING, 0L)
            _timerRemaining.postValue(remaining)
          }
        }
      }

    fun updateTimer(
      timerOption: TimerOption?,
      position: Double? = null,
    ) {
      _timerOption.postValue(timerOption)

      when (timerOption) {
        is DurationTimerOption -> scheduleServiceTimer(timerOption.duration * 60.0, timerOption)

        is CurrentEpisodeTimerOption -> {
          val playingBook = playingBook.value ?: return
          val currentPosition = position ?: totalPosition.value ?: return

          val chapterDuration =
            calculateChapterIndex(playingBook, currentPosition)
              .takeIf { it in playingBook.chapters.indices }
              ?.let { playingBook.chapters[it].duration }
              ?: return

          val chapterPosition =
            calculateChapterPosition(
              book = playingBook,
              overallPosition = currentPosition,
            )

          scheduleServiceTimer(
            delay = (chapterDuration - chapterPosition) / preferences.getPlaybackSpeed(),
            option = timerOption,
          )
        }

        null -> cancelServiceTimer()
      }
    }

    fun rewind() {
      totalPosition
        .value
        ?.let { seekTo(it - getSeekTime(preferences.getSeekTime().rewind)) }
    }

    fun forward() {
      totalPosition
        .value
        ?.let { seekTo(it + getSeekTime(preferences.getSeekTime().forward)) }
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
        return
      }
    }

    fun clearPlayingBook() {
      pause()
      _playingBook.postValue(null)
      preferences.savePlayingBook(null)
    }

    fun setChapterPosition(chapterPosition: Double) {
      val book = playingBook.value ?: return
      val overallPosition = totalPosition.value ?: return

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
        return
      }
    }

    fun prepareAndPlay(book: DetailedItem) {
      when (isPlaybackReady.value) {
        true -> play()
        else -> {
          _playAfterPrepare.postValue(true)
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
      val speed =
        when {
          factor < 0.5f -> 0.5f
          factor > 3f -> 3f
          else -> factor
        }

      if (::mediaController.isInitialized) {
        mediaController.setPlaybackSpeed(speed)
      }

      _playbackSpeed.postValue(speed)
      preferences.savePlaybackSpeed(speed)

      _totalPosition.value?.let { adjustTimer(it) }
    }

    suspend fun preparePlayback(bookId: String) {
      coroutineScope {
        withContext(Dispatchers.IO) {
          mediaChannel
            .fetchBook(bookId)
            .foldAsync(
              onSuccess = { startPreparingPlayback(it) },
              onFailure = { _mediaPreparingError.postValue(true) },
            )
        }
      }
    }

    fun nextTrack() {
      val book = playingBook.value ?: return
      val overallPosition = totalPosition.value ?: return
      val currentIndex = calculateChapterIndex(book, overallPosition)

      val nextChapterIndex = currentIndex + 1
      setChapter(nextChapterIndex)
    }

    fun previousTrack(rewindRequired: Boolean = true) {
      val book = playingBook.value ?: return
      val overallPosition = totalPosition.value ?: return

      val currentIndex = calculateChapterIndex(book, overallPosition)
      val chapterPosition =
        calculateChapterPosition(
          book = book,
          overallPosition = overallPosition,
        )

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
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_SET_TIMER
          putExtra(TIMER_VALUE_EXTRA, delay)
          putExtra(TIMER_OPTION_EXTRA, option)
        }

      context.startService(intent)
    }

    private fun cancelServiceTimer() {
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_CANCEL_TIMER
        }

      context.startService(intent)
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

    fun clearPreparedItem() {
      timerOption
        .value
        ?.let { updateTimer(timerOption = null) }

      _mediaPreparingError.postValue(false)
      _isPlaybackReady.postValue(false)
    }

    private fun startPreparingPlayback(book: DetailedItem) {
      if (_playingBook.value != book) {
        _totalPosition.postValue(0.0)
        _isPlaying.postValue(false)

        _playingBook.postValue(book)
        preferences.savePlayingBook(book)

        val intent =
          Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_PLAYBACK
          }

        when (inBackground()) {
          true -> context.startForegroundService(intent)
          false -> context.startService(intent)
        }
      }
    }

    private fun updateProgress(detailedItem: DetailedItem): Deferred<Unit> =
      CoroutineScope(Dispatchers.Main).async {
        val currentIndex = mediaController.currentMediaItemIndex
        val accumulated = detailedItem.files.take(currentIndex).sumOf { it.duration }
        val currentFilePosition = mediaController.currentPosition / 1000.0

        _totalPosition.postValue(accumulated + currentFilePosition)
      }

    private fun play() {
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_PLAY
        }

      context.startForegroundService(intent)
    }

    private fun pause() {
      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = PlaybackService.ACTION_PAUSE
        }

      context.startService(intent)
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

      val current = totalPosition.value ?: 0.0

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

      val intent =
        Intent(context, PlaybackService::class.java).apply {
          action = ACTION_SEEK_TO
          putExtra(POSITION, safePosition)
        }

      context.startService(intent)
      adjustTimer(safePosition)
    }

    private fun adjustTimer(position: Double) {
      when (_timerOption.value) {
        is CurrentEpisodeTimerOption ->
          updateTimer(
            timerOption = _timerOption.value,
            position = position,
          )

        is DurationTimerOption -> Unit
        null -> Unit
      }
    }

    private fun updateCurrentTrackData() {
      val book = playingBook.value ?: return
      val totalPosition = totalPosition.value ?: return

      val trackIndex = calculateChapterIndex(book, totalPosition)
      val trackPosition = calculateChapterPosition(book, totalPosition)

      _currentChapterIndex.postValue(trackIndex)
      _currentChapterPosition.postValue(trackPosition)
      _currentChapterDuration.postValue(
        book
          .chapters
          .getOrNull(trackIndex)
          ?.duration
          ?: 0.0,
      )
    }

    private companion object {
      private const val CURRENT_TRACK_REPLAY_THRESHOLD = 5

      private fun getSeekTime(option: SeekTimeOption?): Long =
        when (option) {
          SeekTimeOption.SEEK_5 -> 5L
          SeekTimeOption.SEEK_10 -> 10L
          SeekTimeOption.SEEK_15 -> 15L
          SeekTimeOption.SEEK_30 -> 30L
          SeekTimeOption.SEEK_60 -> 60L
          else -> 30L
        }

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
