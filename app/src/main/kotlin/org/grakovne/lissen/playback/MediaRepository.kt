package org.grakovne.lissen.playback

import androidx.annotation.VisibleForTesting
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.ordering.ReorderPlanner
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DetailedItem.Companion.same
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.DefaultTimerActivator
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The playing item as the app sees it: what is playing, where, whether it is ready, and the
 * sleep timer over it. Commands go to the player through [PlayerConnection] and to the
 * service through [PlaybackEventBus]; positions are computed by [PlaybackGeometry] and the
 * bookmarks of the item live in [PlayingBookmarks]. All state is confined to the main thread.
 */
@UnstableApi
@Singleton
class MediaRepository
  @Inject
  constructor(
    private val preferences: PlaybackPreferences,
    private val mediaChannel: LissenMediaProvider,
    private val eventBus: PlaybackEventBus,
    private val defaultTimerActivator: DefaultTimerActivator,
    private val player: PlayerConnection,
    private val mainThread: MainThread,
  ) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    private val playingBookmarks = PlayingBookmarks(mediaChannel, playingBook, scope)
    val bookmarks: StateFlow<List<Bookmark>> = playingBookmarks.bookmarks

    // set by reorderPlayingItem, cleared when the service reports the rebuilt queue ready
    @Volatile
    private var queueRebuildInFlight = false

    private val progressPoller =
      ProgressPoller(
        intervalMs = PROGRESS_UPDATE_INTERVAL_MS,
        schedule = mainThread::postDelayed,
        cancel = mainThread::cancel,
        onTick = { updateProgressWhenReady() },
      )

    private val playerListener =
      object : PlayerConnection.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          _isPlaying.value = isPlaying

          when (isPlaying) {
            true -> {
              progressPoller.start()
              defaultTimerActivator.onPlaybackStarted { updateTimer(it) }
            }

            false -> {
              progressPoller.stop()
              updateProgressWhenReady()
            }
          }
        }

        override fun onPositionDiscontinuity() = updateProgressWhenReady()

        override fun onEnded() {
          player.seekTo(0, 0)
          player.pause()
        }

        override fun onError(error: PlaybackException) {
          Timber.e(error, "Playback error: ${error.errorCodeName}")
          queueRebuildInFlight = false
          progressPoller.stop()
          _isPlaying.value = false
          _playAfterPrepare.value = false
          _mediaPreparingError.value = true
        }
      }

    init {
      player.connect(playerListener) {
        scope.launch { eventBus.events.collect(::onPlaybackEvent) }
      }
    }

    private fun onPlaybackEvent(event: PlaybackEvent) {
      when (event) {
        is PlaybackEvent.PlaybackReady -> {
          onPlaybackReady()
        }

        is PlaybackEvent.TimerExpired -> {
          defaultTimerActivator.onTimerExpired()
          _timerOption.value = null
          pause()
        }

        // emitted by PlaybackTimer on any stop: manual cancel, replacement, or expiry.
        // Timer state is already cleared by the canceling paths, and a replacement
        // re-sets it right after, so there is nothing to reconcile here.
        is PlaybackEvent.TimerCancelled -> {}

        is PlaybackEvent.TimerTick -> {
          _timerRemaining.value = event.remainingSeconds
        }
      }
    }

    private fun onPlaybackReady() {
      // after an in-place rebuild the seeded position is the truth; the controller
      // may still describe the previous queue for one more hop
      val rebuilt = queueRebuildInFlight
      queueRebuildInFlight = false
      val book = preferences.getPlayingItem() ?: return

      if (rebuilt.not()) updateProgress(book)
      if (player.isPlaying) progressPoller.start()

      _isPlaybackReady.value = true

      if (_playAfterPrepare.value) {
        _playAfterPrepare.value = false
        play()
      }
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
          val book = playingBook.value ?: return
          val delay =
            PlaybackGeometry.remainingInChapter(
              book = book,
              totalPosition = position ?: totalPosition.value,
              speed = preferences.getPlaybackSpeed(),
            ) ?: return

          scheduleServiceTimer(delay, timerOption)
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
      val chapter = book.chapters.getOrNull(index)

      when (chapter) {
        null -> Timber.w("Unable to set chapter index=$index for ${book.id}: no such chapter")
        else -> seekTo(chapter.start)
      }
    }

    fun clearPlayingBook() {
      val bookId = _playingBook.value?.id
      Timber.d("Clearing playing book: $bookId")

      progressPoller.stop()
      player.clear()

      _isPlaying.value = false
      _isPlaybackReady.value = false
      _playingBook.value = null
      preferences.clearPlayingItem(bookId)
    }

    fun setTotalPosition(totalPosition: Double) {
      seekTo(totalPosition)
    }

    fun setChapterPosition(chapterPosition: Double) {
      val book = playingBook.value ?: return

      PlaybackGeometry
        .absolutePosition(book, totalPosition.value, chapterPosition)
        ?.let { seekTo(it) }
        ?: Timber.w("Unable to set chapter position=${chapterPosition.toInt()}s for ${book.id}: no current chapter")
    }

    fun prepareAndPlay(book: DetailedItem) {
      Timber.d("prepareAndPlay: bookId=${book.id}, alreadyReady=${isPlaybackReady.value}")

      when (isPlaybackReady.value) {
        true -> {
          play()
        }

        false -> {
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
        false -> play()
      }
    }

    fun setPlaybackSpeed(factor: Float) {
      Timber.d("Setting playback speed to $factor")
      val speed = PlaybackGeometry.clampPlaybackSpeed(factor)

      player.setPlaybackSpeed(speed)
      _playbackSpeed.value = speed
      preferences.savePlaybackSpeed(speed)

      adjustTimer(totalPosition.value)
    }

    suspend fun preparePlayback(
      bookId: String,
      libraryType: LibraryType? = null,
    ) {
      withContext(Dispatchers.IO) {
        mediaChannel
          .fetchBook(bookId, libraryType)
          .foldAsync(
            onSuccess = {
              startPreparingPlayback(it)
              playingBookmarks.refreshFromServerAsync()
            },
            onFailure = { _mediaPreparingError.value = true },
          )
      }
    }

    /**
     * Whether [reorderPlayingItem] would act right now; the UI keeps the ordering sheet inert
     * otherwise, so that a tap never fails silently.
     */
    fun canReorderPlayingItem(itemId: String): Boolean =
      ReorderPlanner.canReorder(
        book = playingBook.value,
        itemId = itemId,
        playbackReady = isPlaybackReady.value,
        storedPlayingItemId = preferences.getPlayingItem()?.id,
      )

    /**
     * Applies a new chapter order to the item already in memory and rebuilds the queue at the
     * same chapter and offset the listener was at. No network involved: the order is a pure
     * function of the chapter keys the item carries. Playback pauses for the rebuild and
     * resumes afterwards if it was running. Returns whether the order is now the requested one.
     */
    fun reorderPlayingItem(
      itemId: String,
      configuration: EpisodeOrderingConfiguration?,
    ): Boolean {
      val book = playingBook.value ?: return false

      if (canReorderPlayingItem(itemId).not()) {
        Timber.w("Ignoring reorder of ${book.id}: not reorderable right now (ready=${isPlaybackReady.value})")
        return false
      }

      val wasPlaying = isPlaying.value
      val plan =
        ReorderPlanner.plan(
          book = book,
          configuration = configuration,
          totalPosition = totalPosition.value,
          now = System.currentTimeMillis(),
        ) ?: return true

      Timber.d("Reordering playing item ${book.id} to $configuration at ${plan.item.progress?.currentTime} (wasPlaying=$wasPlaying)")

      pause()
      _mediaPreparingError.value = false
      _isPlaybackReady.value = false

      _playAfterPrepare.value = wasPlaying
      startPreparingPlayback(plan.item)
      playingBookmarks.followReorder(from = book, to = plan.item)
      // after startPreparingPlayback, which resets the flag for every fresh preparation
      queueRebuildInFlight = true

      plan.item.progress?.let { _totalPosition.value = it.currentTime }
      updateCurrentTrackData()

      return true
    }

    fun nextTrack() {
      val book = playingBook.value ?: return
      val next = PlaybackGeometry.nextChapter(book, totalPosition.value)
      Timber.d("Next track: bookId=${book.id}, currentChapter=${next - 1} -> $next")

      setChapter(next)
    }

    fun previousTrack(rewindRequired: Boolean = true) {
      val book = playingBook.value ?: return
      val position = totalPosition.value
      Timber.d("Previous track: bookId=${book.id}, position=${position.toInt()}s, rewind=$rewindRequired")

      PlaybackGeometry
        .previousChapter(book, position, rewindRequired)
        ?.let { setChapter(it) }
    }

    fun clearPreparedItem() {
      if (timerOption.value != null) {
        _timerOption.value = null
        cancelServiceTimer()
      }

      defaultTimerActivator.onNewBookPrepared()
      _mediaPreparingError.value = false
      _playAfterPrepare.value = false
      _isPlaybackReady.value = false
      queueRebuildInFlight = false
    }

    fun registerPlayingBook(book: DetailedItem) {
      val current = _playingBook.value
      val sameBook = current?.same(book) ?: false

      // the same item in another order while its queue is being rebuilt in place: the session
      // fetched it before the new order was stored; the rebuild in flight is the truth
      if (sameBook.not() && queueRebuildInFlight && current?.id == book.id) {
        Timber.w("Ignoring registration of ${book.id} in another order: a rebuild is in flight")
        return
      }

      if (sameBook.not()) {
        Timber.d("Registering playing book prepared via media session: ${book.id}")

        _totalPosition.value = book.progress?.currentTime ?: 0.0
        _playingBook.value = book
        // readiness arrived outside the service: whatever rebuild was in flight is over
        queueRebuildInFlight = false
        _isPlaybackReady.value = true
        playingBookmarks.refreshFromServerAsync()
      }
    }

    /**
     * Records a bookmark at the current position, see [PlayingBookmarks.create]. Callers may
     * come from any dispatcher: the position is main-confined like the rest of the repository,
     * so the work hops to the main thread first.
     */
    suspend fun createBookmark(title: String? = null): Bookmark? =
      withContext(Dispatchers.Main.immediate) {
        val book = _playingBook.value ?: return@withContext null
        playingBookmarks.create(book, _totalPosition.value, title)
      }

    suspend fun dropBookmark(bookmark: Bookmark) = playingBookmarks.drop(bookmark)

    suspend fun updateBookmarks() = playingBookmarks.refreshFromServer()

    /**
     * Drops the session binding. The service stays alive for as long as any controller is bound
     * to it, and a repository that is discarded without this call keeps it alive until it is
     * garbage collected. Only test graphs discard repositories: the app has one for its lifetime.
     */
    @VisibleForTesting
    fun release() {
      progressPoller.stop()
      player.release()
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

    private fun startPreparingPlayback(book: DetailedItem) {
      val sameBook = _playingBook.value?.same(book) ?: false
      queueRebuildInFlight = false

      when (sameBook) {
        true -> {
          _isPlaybackReady.value = true
        }

        false -> {
          _totalPosition.value = 0.0
          _isPlaying.value = false

          _playingBook.value = book
          preferences.savePlayingItem(book)

          eventBus.send(PlaybackCommand.PreparePlayback)
        }
      }
    }

    /**
     * While an in-place queue rebuild is in flight the controller still describes the previous
     * queue, so a position computed from it against the reordered item would be meaningless.
     * Only that window is skipped: playback that carries on with the previous item while a new
     * one fails to load, or while the screen waits to resume, keeps its progress live.
     */
    private fun updateProgressWhenReady() {
      if (queueRebuildInFlight) return
      _playingBook.value?.let { updateProgress(it) }
    }

    private fun updateProgress(book: DetailedItem) {
      _totalPosition.value =
        PlaybackGeometry.totalPosition(
          book = book,
          mediaItemIndex = player.currentMediaItemIndex,
          filePosition = player.currentPositionMs / 1000.0,
        )

      updateCurrentTrackData()
    }

    private fun updateCurrentTrackData() {
      val book = playingBook.value ?: return
      val progress = PlaybackGeometry.chapterProgress(book, totalPosition.value)

      _currentChapterIndex.value = progress.index
      _currentChapterPosition.value = progress.position
      _currentChapterDuration.value = progress.duration
    }

    private fun play() {
      mainThread.run { player.play(preferences.getPlaybackSpeed()) }
    }

    private fun pause() {
      mainThread.run { player.pause() }
    }

    private fun seekTo(position: Double) {
      val book = playingBook.value ?: return

      // the controller still holds the previous queue: a seek computed for the new order would
      // land in the wrong episode, and the position read back would be meaningless
      if (queueRebuildInFlight) {
        Timber.d("Ignoring seek to ${position.toInt()}s: the queue is being rebuilt")
        return
      }

      val target = PlaybackGeometry.resolveSeek(book, from = totalPosition.value, to = position)
      if (target == null) {
        Timber.d("Tried to seek on the empty book")
        return
      }

      mainThread.run {
        if (player.isConnected) {
          player.seekTo(target.chapterIndex, target.chapterPositionMs)
          updateProgressWhenReady()
        }
      }

      adjustTimer(target.totalPosition)
    }

    private fun adjustTimer(position: Double) {
      when (val option = _timerOption.value) {
        is CurrentEpisodeTimerOption -> {
          updateTimer(timerOption = option, position = position)
        }

        is DurationTimerOption, null -> {}
      }
    }

    private companion object {
      private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

      private fun getSeekTime(seconds: Int?): Long = seconds?.toLong() ?: 30L
    }
  }
