package org.grakovne.lissen.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.AutoSkipConfiguration
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skips the intro and the outro of every chapter of an item, as configured for that item. The
 * rules live in [AutoSkipPlanner]; this is the part that listens to the player and acts.
 *
 * The intro is skipped only while playback is actually running: a chapter that is entered (an
 * automatic transition, "next", a pick from the list, a freshly built queue) marks its intro
 * pending, and the first moment of playback seeks past it. The outro is a [PlayerMessage]
 * planted where it begins; reaching it ends the chapter for the server, then moves on to the
 * next chapter straight past its own intro, or to the very end when nothing follows. A user who
 * moves back into an outro keeps it, and a sleep timer armed for the end of the episode takes
 * it instead. Every decision is posted to the main looper and re-checked
 * there, because player callbacks arrive synchronously inside the call that caused them.
 */
@Singleton
@OptIn(UnstableApi::class)
class AutoSkipService
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val libraryPreferences: LibraryPreferences,
    private val syncState: SyncStateStore,
    private val playbackTimer: PlaybackTimer,
    private val synchronization: PlaybackSynchronizationService,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var marks = SkipMarks()
    private var planted: PlantedOutros? = null
    private var messages: List<PlayerMessage> = emptyList()

    // guards against the player's own callbacks: the seek that places a new queue is not a user
    // seek, and the own seeks all land where the rules are neutral, which the flag keeps from
    // being a coincidence
    private var awaitingQueueLanding = false
    private var seekingQuietly = false

    private val listener =
      object : Player.Listener {
        override fun onTimelineChanged(
          timeline: Timeline,
          reason: Int,
        ) {
          if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return

          // a new queue: nothing of the old one applies, the player has already dropped the old
          // messages with the old items, and the seek that places the player in the queue is still
          // to come inside this same main task, so the landing is read afterwards
          marks = SkipMarks()
          messages = emptyList()
          planted = null
          awaitingQueueLanding = true
          post {
            awaitingQueueLanding = false
            plantOutroMessages()
            onQueueLanded()
          }
        }

        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int,
        ) {
          if (seekingQuietly || awaitingQueueLanding) return

          when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
              onChapterFollowed(oldPosition, newPosition)
            }

            Player.DISCONTINUITY_REASON_SEEK -> {
              onSeekLanded(oldPosition, newPosition)
            }

            else -> {}
          }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) post { onPlaybackRunning() }
        }
      }

    override fun onCreate() {
      player.addListener(listener)
      scope.launch { libraryPreferences.autoSkipFlow.collect { plantOutroMessages() } }
    }

    /** A file boundary inside a chapter is a transition too, but not into another chapter. */
    private fun onChapterFollowed(
      from: Player.PositionInfo,
      to: Player.PositionInfo,
    ) {
      if (from.mediaItemIndex == to.mediaItemIndex) return

      enter(to.mediaItemIndex)
    }

    private fun onSeekLanded(
      from: Player.PositionInfo,
      to: Player.PositionInfo,
    ) {
      val index = to.mediaItemIndex
      val landing = AutoSkipPlanner.landing(from.mediaItemIndex, from.positionMs, index, to.positionMs, chapterAt(index))

      marks =
        when (landing) {
          SeekLanding.ENTRY -> marks.entered(index)
          SeekLanding.START_KEPT, SeekLanding.OUTRO_FORWARD -> marks.copy(pendingIntro = null)
          SeekLanding.OUTRO_KEPT -> marks.copy(pendingIntro = null, keptOutro = index)
          SeekLanding.ELSEWHERE -> marks.copy(pendingIntro = null, keptOutro = marks.keptOutro.takeUnless { it == index })
        }
      if (landing == SeekLanding.ENTRY) post { applyPendingIntro() }
    }

    /** The queue was just built or replaced: where the player was put is read the same way as a landing. */
    private fun onQueueLanded() {
      if (player.currentPosition < AutoSkipPlanner.CHAPTER_ENTRY_TOLERANCE_MS) enter(player.currentMediaItemIndex)
      applyPendingIntro()
    }

    private fun enter(index: Int) {
      marks = marks.entered(index)
      post { applyPendingIntro() }
    }

    private fun onPlaybackRunning() {
      applyPendingIntro()

      // resumed inside an outro (after the sleep timer, after buffering, after a seek forward into
      // it): moved on when something follows; the outro of the last chapter is played, ending the
      // item under a listener who just pressed play would leave nothing playing
      val index = player.currentMediaItemIndex
      val book = currentBook() ?: return
      val chapter = chapterAt(book, index) ?: return
      if (!chapter.outroReached(player.currentPosition)) return

      when (val exit = AutoSkipPlanner.outroExit(book, index, chapter.configuration)) {
        is OutroExit.Next -> {
          leaveOutro(book, index, exit)
        }

        is OutroExit.End -> {}
      }
    }

    private fun applyPendingIntro() {
      val index = marks.pendingIntro ?: return
      if (!player.isPlaying || player.currentMediaItemIndex != index) return

      marks = marks.copy(pendingIntro = null)
      val target = chapterAt(index)?.introTargetMs(player.currentPosition) ?: return

      Timber.d("Auto-skip intro: chapter=$index, targetMs=$target")
      seekQuietly(index, target)
    }

    /** Playback crossed into the outro of [index]: a delivered message is the proof, the position is not re-read. */
    private fun onOutroCrossed(index: Int) {
      if (player.currentMediaItemIndex != index) return
      val book = currentBook() ?: return
      val chapter = chapterAt(book, index) ?: return

      // a sleep timer set to the end of this episode is armed for this very moment and wins: it
      // pauses the player itself, whichever of the two fires first
      if (playbackTimer.isEpisodeTimerRunning) {
        Timber.d("Auto-skip outro: chapter=$index, takenBy=episodeTimer")
        return
      }

      leaveOutro(book, index, AutoSkipPlanner.outroExit(book, index, chapter.configuration))
    }

    private fun leaveOutro(
      book: DetailedItem,
      index: Int,
      exit: OutroExit,
    ) {
      if (!player.isPlaying || marks.keptOutro == index) return

      synchronization.reportChapterEnd(index)

      when (exit) {
        is OutroExit.Next -> {
          Timber.d("Auto-skip outro: chapter=$index, next=${exit.index}, startMs=${exit.startMs}")
          marks = SkipMarks()
          seekQuietly(exit.index, exit.startMs)
          rearmEpisodeTimer(book, exit.index, exit.startMs)
        }

        is OutroExit.End -> {
          // the player may report playback running once more before it ends: the chapter is done
          Timber.d("Auto-skip outro: chapter=$index, next=none, endMs=${exit.atMs}")
          marks = marks.copy(keptOutro = index)
          seekQuietly(index, exit.atMs)
        }
      }
    }

    /** The own seeks bypass the repository, which re-arms the episode timer after its seeks, so it is done here. */
    private fun rearmEpisodeTimer(
      book: DetailedItem,
      index: Int,
      positionMs: Long,
    ) {
      if (!playbackTimer.isEpisodeTimerRunning) return
      val chapterStart = book.chapters.getOrNull(index)?.start ?: return
      val remaining =
        PlaybackGeometry.remainingInChapter(
          book = book,
          totalPosition = chapterStart + positionMs / 1000.0,
          speed = player.playbackParameters.speed,
          autoSkip = libraryPreferences.getAutoSkip(book.id),
        ) ?: return

      Timber.d("Auto-skip timer: chapter=$index, remainingSeconds=${remaining.toInt()}")
      playbackTimer.startTimer(remaining, CurrentEpisodeTimerOption)
    }

    /**
     * One message per chapter with an outro. Planted again only when the queue, the item or the
     * configuration changed: a cancelled message lingers in the player until it is crossed, and
     * every send sorts the player's message list.
     */
    private fun plantOutroMessages() {
      val wanted = currentBook()?.let { PlantedOutros(it, libraryPreferences.getAutoSkip(it.id)) }
      if (wanted == planted) return

      messages.forEach { it.cancel() }
      planted = wanted
      messages =
        wanted
          ?.let { AutoSkipPlanner.outroPositions(it.book, it.configuration) }
          .orEmpty()
          .map { (index, positionMs) ->
            player
              .createMessage { _, _ -> post { onOutroCrossed(index) } }
              .setPosition(index, positionMs)
              .setLooper(Looper.getMainLooper())
              .setDeleteAfterDelivery(false)
              .send()
          }
      Timber.d("Auto-skip outro messages: count=${messages.size}, item=${wanted?.book?.id}")
    }

    private fun seekQuietly(
      index: Int,
      positionMs: Long,
    ) {
      seekingQuietly = true
      try {
        player.seekTo(index, positionMs)
      } finally {
        seekingQuietly = false
      }
    }

    private fun post(action: () -> Unit) {
      scope.launch { action() }
    }

    /**
     * The item behind the queue, as the synchronization knows it: the queue is built from it in
     * the same breath, one media item per chapter. The media items themselves carry neither the
     * item (no tag without a URI) nor an id (the source factory rebuilds them).
     */
    private fun currentBook(): DetailedItem? = syncState.value.item?.takeIf { it.chapters.size == player.mediaItemCount }

    private fun chapterAt(index: Int): SkippableChapter? = currentBook()?.let { chapterAt(it, index) }

    private fun chapterAt(
      book: DetailedItem,
      index: Int,
    ): SkippableChapter? = book.chapters.getOrNull(index)?.let { libraryPreferences.getAutoSkip(book.id).skippable(it.durationMs) }

    /** What the listener is owed: an intro to skip once playback runs, an outro that is played out. */
    private data class SkipMarks(
      val pendingIntro: Int? = null,
      val keptOutro: Int? = null,
    ) {
      fun entered(index: Int) = SkipMarks(pendingIntro = index, keptOutro = keptOutro.takeUnless { it == index })
    }

    /** What the planted messages describe; a queue of the same item in the same order with the same configuration needs no new ones. */
    private data class PlantedOutros(
      val book: DetailedItem,
      val configuration: AutoSkipConfiguration,
    )
  }
