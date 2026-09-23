package org.grakovne.lissen.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.content.ordering.ChapterOrdering.end
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
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.round

@UnstableApi
@Singleton
class MediaRepository
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: PlaybackPreferences,
    private val mediaChannel: LissenMediaProvider,
    private val eventBus: PlaybackEventBus,
    private val defaultTimerActivator: DefaultTimerActivator,
  ) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mediaController: MediaController
    private val deferredControllerActions = DeferredActions()

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

    // set by reorderPlayingItem, cleared when the service reports the rebuilt queue ready
    @Volatile
    private var queueRebuildInFlight = false

    // the item (and order) the displayed bookmark positions were translated for
    @Volatile
    private var bookmarksItem: DetailedItem? = null

    private val handler = Handler(Looper.getMainLooper())

    private val progressPoller =
      ProgressPoller(
        intervalMs = PROGRESS_UPDATE_INTERVAL_MS,
        schedule = { runnable, delay -> handler.postDelayed(runnable, delay) },
        cancel = { runnable -> handler.removeCallbacks(runnable) },
        onTick = { updateProgressWhenReady() },
      )

    private val futureController: ListenableFuture<MediaController> =
      MediaController.Builder(context, token).buildAsync()

    init {
      Futures.addCallback(
        futureController,
        object : FutureCallback<MediaController> {
          override fun onSuccess(controller: MediaController) {
            mediaController = controller

            scope.launch {
              eventBus.events.collect { event ->
                when (event) {
                  is PlaybackEvent.PlaybackReady -> {
                    // after an in-place rebuild the seeded position is the truth; the controller
                    // may still describe the previous queue for one more hop
                    val rebuilt = queueRebuildInFlight
                    queueRebuildInFlight = false
                    val book = preferences.getPlayingItem()
                    book?.let {
                      if (rebuilt.not()) updateProgress(book)

                      if (mediaController.isPlaying) {
                        progressPoller.start()
                      }

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

                  // emitted by PlaybackTimer on any stop: manual cancel, replacement, or expiry.
                  // Timer state is already cleared by the canceling paths, and a replacement
                  // re-sets it right after, so there is nothing to reconcile here.
                  is PlaybackEvent.TimerCancelled -> {}

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

                  when {
                    isPlaying -> {
                      progressPoller.start()
                      defaultTimerActivator.onPlaybackStarted { updateTimer(it) }
                    }

                    else -> {
                      progressPoller.stop()
                      updateProgressWhenReady()
                    }
                  }
                }

                override fun onPositionDiscontinuity(
                  oldPosition: Player.PositionInfo,
                  newPosition: Player.PositionInfo,
                  reason: Int,
                ) {
                  updateProgressWhenReady()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                  if (playbackState == Player.STATE_ENDED) {
                    mediaController.seekTo(0, 0)
                    mediaController.pause()
                  }
                }

                override fun onPlayerError(error: PlaybackException) {
                  Timber.e(error, "Playback error: ${error.errorCodeName}")
                  queueRebuildInFlight = false
                  progressPoller.stop()
                  _isPlaying.value = false
                  _playAfterPrepare.value = false
                  _mediaPreparingError.value = true
                }
              },
            )

            deferredControllerActions.drain()
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
      val bookId = _playingBook.value?.id
      Timber.d("Clearing playing book: $bookId")

      progressPoller.stop()

      if (::mediaController.isInitialized) {
        mediaController.stop()
        mediaController.clearMediaItems()
      }

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

    suspend fun preparePlayback(
      bookId: String,
      libraryType: LibraryType? = null,
    ) {
      coroutineScope {
        withContext(Dispatchers.IO) {
          mediaChannel
            .fetchBook(bookId, libraryType)
            .foldAsync(
              onSuccess = {
                startPreparingPlayback(it)
                refreshBookmarksFromServer()
              },
              onFailure = { _mediaPreparingError.value = true },
            )
        }
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

      // the list in memory follows at once, so nothing can act on positions of the old order;
      // the exact stored values, translated for the new order, replace it as soon as they are read
      _bookmarks.value =
        _bookmarks.value.map {
          when (it.libraryItemId == book.id) {
            true -> it.copy(totalPosition = ChapterOrdering.translate(book, plan.item, it.totalPosition))
            false -> it
          }
        }
      bookmarksItem = plan.item
      scope.launch { refreshBookmarksFromCache(book.id) }
      // after startPreparingPlayback, which resets the flag for every fresh preparation
      queueRebuildInFlight = true

      plan.item.progress?.let { _totalPosition.value = it.currentTime }
      updateCurrentTrackData()

      return true
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
        refreshBookmarksFromServer()
      }
    }

    /**
     * Every freshly prepared item pulls its bookmarks from the server, so that ones added from
     * another device show up whichever way the item arrived (the screen, the widget, the media
     * session). A reorder goes through [startPreparingPlayback] directly and is not a fresh
     * item: it translates the list it already has and re-reads the cache.
     */
    private fun refreshBookmarksFromServer() {
      scope.launch { updateBookmarks() }
    }

    private fun startPreparingPlayback(book: DetailedItem) {
      val sameBook = _playingBook.value?.same(book) ?: false
      queueRebuildInFlight = false

      if (sameBook.not()) {
        _totalPosition.value = 0.0
        _isPlaying.value = false

        _playingBook.value = book
        preferences.savePlayingItem(book)

        eventBus.send(PlaybackCommand.PreparePlayback)
      } else {
        _isPlaybackReady.value = true
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

    private fun updateProgress(detailedItem: DetailedItem) {
      val currentIndex = mediaController.currentMediaItemIndex
      val chapters = detailedItem.chapters
      val accumulated = chapters.take(currentIndex.coerceIn(0, chapters.size)).sumOf { it.duration }
      val currentFilePosition = mediaController.currentPosition / 1000.0

      val newPosition = accumulated + currentFilePosition
      _totalPosition.value = newPosition
      updateCurrentTrackData()
    }

    private fun play() {
      withMain {
        if (!::mediaController.isInitialized) {
          Timber.w("play() requested before media controller connected; deferring until connected")
          deferredControllerActions.defer { play() }
          return@withMain
        }

        mediaController.prepare()
        mediaController.setPlaybackSpeed(preferences.getPlaybackSpeed())
        mediaController.play()
      }
    }

    private fun pause() {
      withMain {
        if (::mediaController.isInitialized) {
          mediaController.pause()
        }
      }
    }

    private fun seekTo(position: Double) {
      val book = playingBook.value ?: return

      if (book.chapters.isEmpty()) {
        Timber.d("Tried to seek on the empty book")
        return
      }

      // the controller still holds the previous queue: a seek computed for the new order would
      // land in the wrong episode, and the position read back would be meaningless
      if (queueRebuildInFlight) {
        Timber.d("Ignoring seek to ${position.toInt()}s: the queue is being rebuilt")
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

      val startIndex = calculateChapterIndex(book, safePosition)
      if (startIndex in book.chapters.indices && book.chapters[startIndex].available.not()) {
        val forward = (startIndex..book.chapters.lastIndex).firstOrNull { book.chapters[it].available }
        val backward = (startIndex downTo 0).firstOrNull { book.chapters[it].available }

        val target =
          when (direction) {
            ScrollingDirection.FORWARD -> forward ?: backward
            ScrollingDirection.BACKWARD -> backward ?: forward
          }

        target?.let { safePosition = book.chapters[it].start }
      }

      val (chapterIndex, chapterPosition) = calculateChapterIndexAndPosition(book, safePosition)

      withMain {
        if (::mediaController.isInitialized) {
          mediaController.seekTo(chapterIndex, (chapterPosition * 1000).toLong())
          updateProgressWhenReady()
        }
      }

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

      val (trackIndex, trackPosition) = calculateChapterIndexAndPosition(book, totalPosition)

      _currentChapterIndex.value = trackIndex
      _currentChapterPosition.value = trackPosition
      _currentChapterDuration.value =
        book
          .chapters
          .getOrNull(trackIndex)
          ?.duration
          ?: 0.0
    }

    /** Returns the stored bookmark, or null when there is nothing playing to bookmark. */
    suspend fun createBookmark(title: String? = null): Bookmark? {
      Timber.d("Creating bookmark for ${_playingBook.value?.id} at position=${_totalPosition.value.toInt()}s")
      val playingBook = _playingBook.value ?: return null
      // a live position may overshoot the declared end by a little: that is the end, not nowhere
      val totalPosition = _totalPosition.value.coerceAtMost(playingBook.end() ?: _totalPosition.value)

      // the same boundary rule as the stored position, so the title names the episode the
      // bookmark is actually in, however far apart neighbours are in the listener's order
      val location = ChapterOrdering.locate(playingBook, totalPosition)
      val currentChapter = location?.let { l -> playingBook.chapters.firstOrNull { it.id == l.chapterId } }
      if (currentChapter == null) {
        Timber.w("Unable to create bookmark: no chapter at position=${totalPosition.toInt()}s")
        return null
      }
      val chapterPosition = location.offset

      val bookmarkTitle =
        when (title) {
          null -> buildBookmarkTitle(currentChapter.title, chapterPosition)
          else -> title
        }

      val created =
        mediaChannel
          .createBookmark(
            libraryItemId = playingBook.id,
            totalPosition = ChapterOrdering.storedBookmarkPosition(playingBook, totalPosition),
            title = bookmarkTitle,
          )

      refreshBookmarksFromCache(playingBook.id)
      return created
    }

    /**
     * The bookmark handed in carries a display position. The stored one is found by translating
     * the stored list the same way the display list was made, so the stored value goes back
     * exactly as it is, however it translated: a position outside the item or on the very end
     * has no faithful way back through the numbers alone.
     */
    suspend fun dropBookmark(bookmark: Bookmark) {
      Timber.d("Dropping bookmark for ${bookmark.libraryItemId} at position=${bookmark.totalPosition.toInt()}s")
      // the item the displayed list was built for, which is not necessarily the one playing now
      val playingBook = bookmarksItem?.takeIf { it.id == bookmark.libraryItemId }

      val stored =
        when (playingBook != null) {
          true -> {
            val candidates = mediaChannel.provideBookmarks(bookmark.libraryItemId)
            val displayed = candidates.zip(candidates.inPlayingOrder(playingBook))

            // the display value may have been made by another translation path (one ulp off),
            // and a draft's createdAt is replaced by the server's once synced: match loosely
            displayed
              .firstOrNull { (_, shown) ->
                shown.totalPosition.isSameSecondAs(bookmark.totalPosition) &&
                  shown.createdAt == bookmark.createdAt
              }?.first
              ?: displayed.firstOrNull { (_, shown) -> shown.totalPosition.isSameSecondAs(bookmark.totalPosition) }?.first
              ?: displayed.firstOrNull { (_, shown) -> shown.createdAt == bookmark.createdAt }?.first
              ?: bookmark.copy(totalPosition = round(ChapterOrdering.toCanonicalPosition(playingBook, bookmark.totalPosition)))
          }

          false -> {
            bookmark
          }
        }

      mediaChannel.dropBookmark(bookmark = stored)

      refreshBookmarksFromCache(bookmark.libraryItemId)
    }

    /**
     * The stored (canonical) bookmarks of [itemId], translated for whatever order is playing at
     * the moment of the write, so that a reorder landing during the read cannot leave the list
     * in the previous order.
     */
    private suspend fun refreshBookmarksFromCache(itemId: String) {
      val stored = withContext(Dispatchers.IO) { mediaChannel.provideBookmarks(itemId) }
      val book = _playingBook.value

      // another item started playing meanwhile: its own refresh will follow, these rows are not its
      if (book?.id != itemId) return

      bookmarksItem = book
      _bookmarks.value = stored.inPlayingOrder(book)
    }

    private fun Double.isSameSecondAs(other: Double): Boolean = abs(this - other) < BOOKMARK_MATCH_EPSILON

    suspend fun updateBookmarks() {
      val book = playingBook.value ?: return
      val bookmarks = withContext(Dispatchers.IO) { mediaChannel.updateAndProvideBookmarks(book.id) }

      // the item may have been reordered meanwhile: translate for the order that is playing now;
      // another item playing meanwhile means these rows are not its
      val current = playingBook.value
      if (current?.id != book.id) return

      bookmarksItem = current
      _bookmarks.value = bookmarks.inPlayingOrder(current)
    }

    /**
     * Bookmarks are stored and sent to the server as positions in the canonical order, whole
     * seconds; the player and the UI work in the order the listener has chosen. Display
     * positions are translated exactly and never rounded: rounding could push one onto a
     * chapter boundary, which belongs to the next chapter. Only the way back to canonical
     * (see [dropBookmark]) rounds, because there the true value is a whole second and double
     * arithmetic may have left 1968.9999 of it.
     */
    private fun List<Bookmark>.inPlayingOrder(book: DetailedItem?): List<Bookmark> {
      if (book == null) return this

      val canonical = ChapterOrdering.canonical(book)

      return map {
        when (it.libraryItemId == book.id) {
          true -> it.copy(totalPosition = ChapterOrdering.translate(canonical, book, it.totalPosition))
          false -> it
        }
      }
    }

    /**
     * Drops the session binding. The service stays alive for as long as any controller is bound
     * to it, and a repository that is discarded without this call keeps it alive until it is
     * garbage collected. Only test graphs discard repositories: the app has one for its lifetime.
     */
    @VisibleForTesting
    fun release() {
      progressPoller.stop()
      MediaController.releaseFuture(futureController)
    }

    private fun withMain(action: () -> Unit) {
      when (Looper.myLooper() == Looper.getMainLooper()) {
        true -> action()
        false -> handler.post(action)
      }
    }

    private companion object {
      private const val CURRENT_TRACK_REPLAY_THRESHOLD = 5

      // two translations of the same stored second differ by an ulp at most
      private const val BOOKMARK_MATCH_EPSILON = 1e-3
      private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

      private fun getSeekTime(seconds: Int?): Long = seconds?.toLong() ?: 30L
    }
  }

enum class ScrollingDirection {
  FORWARD,
  BACKWARD,
}
