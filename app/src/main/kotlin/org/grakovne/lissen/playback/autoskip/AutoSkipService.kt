package org.grakovne.lissen.playback.autoskip

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
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skips the intro and the outro of every chapter of an item, as configured for that item. The
 * arithmetic lives in [AutoSkipPlanner]; this is the part that listens to the player and acts.
 *
 * Whatever playback reaches by itself is skipped. A chapter that follows on its own, or is
 * entered at its very start, or holds the position a queue was placed at, is owed its skips:
 * the first moment of playback seeks past the intro, or on out of the outro. The outro is a
 * [PlayerMessage] planted where it begins; reaching it ends the chapter for the server, then
 * moves on to the next chapter straight past its own intro, or to the very end when nothing
 * follows. A seek by the listener is theirs: whatever it lands in is played as it is, except
 * the "forward" step, which is the player's own movement ([PlaybackSteps]). A sleep
 * timer armed for the end of the episode takes the outro instead, and the skip it held back is
 * done when playback runs again. Every decision is posted to the main looper and re-checked
 * there, because player callbacks arrive synchronously inside the call that caused them.
 */
@Singleton
@OptIn(UnstableApi::class)
class AutoSkipService
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val preferences: AutoSkipPreferences,
    private val syncState: SyncStateStore,
    private val playbackTimer: PlaybackTimer,
    private val synchronization: PlaybackSynchronizationService,
    private val steps: PlaybackSteps,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The chapter playback reached by itself, whose skips are done once playback runs. */
    private var owed: Int? = null
    private var planted: PlantedOutros? = null

    /**
     * The chapter whose outro ended the item. A chapter with less audio than the server says has
     * its outro message at the very end of the audio, and the seek to the end lands a hair short
     * of it: crossed again, the message would end the item again, and again.
     */
    private var ended: Int? = null

    private val listener =
      object : Player.Listener {
        override fun onTimelineChanged(
          timeline: Timeline,
          reason: Int,
        ) {
          if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return

          // a new queue: nothing of the old one applies, the player has already dropped the old
          // messages with the old items, and the seek that places the player in the queue is still
          // to come inside this same main task, so the chapter is read afterwards
          owed = null
          planted = null
          ended = null
          post {
            plantOutroMessages()
            reach(player.currentMediaItemIndex)
          }
        }

        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int,
        ) {
          val another = newPosition.mediaItemIndex != oldPosition.mediaItemIndex

          when (reason) {
            // a file boundary inside a chapter is a transition too, but not into another chapter
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
              if (another) reach(newPosition.mediaItemIndex)
            }

            // "next" and a pick from the list: the very start of another chapter is entered;
            // a "forward" step is the player's own; anywhere else is the listener's
            Player.DISCONTINUITY_REASON_SEEK -> {
              val step = steps.take(newPosition)
              when (step || (another && newPosition.positionMs == 0L)) {
                true -> reach(newPosition.mediaItemIndex)
                false -> listenerMoved()
              }
            }

            else -> {}
          }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) post { settleOwed() }
        }
      }

    override fun onCreate() {
      player.addListener(listener)
      scope.launch { preferences.flow.collect { plantOutroMessages() } }
    }

    private fun reach(index: Int) {
      owed = index
      ended = null
      post { settleOwed() }
    }

    /** Wherever the listener moved to is theirs: nothing is owed, and an outro crossed after it is skipped again. */
    private fun listenerMoved() {
      owed = null
      ended = null
    }

    /** Playback runs in the chapter it is owed to: past the intro, or on out of the outro. */
    private fun settleOwed() {
      val index = owed ?: return
      if (!player.isPlaying) return

      owed = null
      if (player.currentMediaItemIndex != index) return
      val book = currentBook() ?: return
      val chapter = chapterAt(book, index) ?: return
      val position = player.currentPosition

      when (val target = chapter.introTargetMs(position)) {
        null -> {
          if (chapter.outroReached(position)) leaveOutroOnResume(book, index, chapter.configuration)
        }

        else -> {
          Timber.d("Auto-skip intro: chapter=$index, targetMs=$target")
          player.seekTo(index, target)
        }
      }
    }

    /**
     * Resumed inside an outro the timer took, or one a queue was placed in: moved on when
     * something follows. The outro of the last chapter is played, because ending the item under
     * a listener who just pressed play would leave nothing playing.
     */
    private fun leaveOutroOnResume(
      book: DetailedItem,
      index: Int,
      configuration: AutoSkipConfiguration,
    ) {
      val exit = AutoSkipPlanner.outroExit(book, index, configuration)
      if (exit is OutroExit.Next) leaveOutro(book, index, exit)
    }

    /** Playback crossed into the outro of [index]: a delivered message is the proof, the position is not re-read. */
    private fun onOutroCrossed(index: Int) {
      if (player.currentMediaItemIndex != index || ended == index) return
      val book = currentBook() ?: return
      val chapter = chapterAt(book, index) ?: return

      when {
        // a sleep timer armed for the end of this episode wins: it pauses the player itself,
        // whichever of the two fires first, and the skip waits for the listener to come back
        playbackTimer.isEpisodeTimerRunning || !player.isPlaying -> {
          Timber.d("Auto-skip outro: chapter=$index, takenBy=pause")
          owed = index
        }

        else -> {
          leaveOutro(book, index, AutoSkipPlanner.outroExit(book, index, chapter.configuration))
        }
      }
    }

    /** The seeks are the player's own: an episode timer is re-armed by the repository on the discontinuity they cause. */
    private fun leaveOutro(
      book: DetailedItem,
      index: Int,
      exit: OutroExit,
    ) {
      synchronization.reportChapterEnd(index)

      when (exit) {
        is OutroExit.Next -> {
          Timber.d("Auto-skip outro: chapter=$index, next=${exit.index}, startMs=${exit.startMs}")
          player.seekTo(exit.index, exit.startMs)
        }

        is OutroExit.End -> {
          val endMs = book.chapters[index].durationMs
          Timber.d("Auto-skip outro: chapter=$index, next=none, endMs=$endMs")
          player.seekTo(index, endMs)
          // after the seek: its own discontinuity reads as the listener's and clears the mark
          ended = index
        }
      }
    }

    /**
     * One message per chapter with an outro. Planted again only when the queue, the item or the
     * configuration changed: a cancelled message lingers in the player until it is crossed, and
     * every send sorts the player's message list.
     */
    private fun plantOutroMessages() {
      val wanted = currentBook()?.let { OutroPlan(it, preferences.get(it.id)) }
      if (wanted == planted?.plan) return

      planted?.messages?.forEach { it.cancel() }
      planted =
        wanted?.let { plan ->
          val messages =
            AutoSkipPlanner.outroPositions(plan.book, plan.configuration).map { (index, positionMs) ->
              player
                .createMessage { _, _ -> post { onOutroCrossed(index) } }
                .setPosition(index, positionMs)
                .setLooper(Looper.getMainLooper())
                .setDeleteAfterDelivery(false)
                .send()
            }
          PlantedOutros(plan, messages)
        }
      Timber.d("Auto-skip outro messages: count=${planted?.messages?.size ?: 0}, item=${wanted?.book?.id}")
    }

    private fun post(action: () -> Unit) {
      scope.launch { action() }
    }

    /**
     * The item behind the queue, as the synchronization knows it: the queue is built from it in
     * the same breath, one media item per chapter. The media items themselves carry neither the
     * item (no tag without a URI) nor an id (the source factory rebuilds them without one).
     */
    private fun currentBook(): DetailedItem? = syncState.value.item?.takeIf { it.chapters.size == player.mediaItemCount }

    private fun chapterAt(
      book: DetailedItem,
      index: Int,
    ): SkippableChapter? = book.chapters.getOrNull(index)?.let { preferences.get(book.id).skippable(it.durationMs) }

    /** What the planted messages describe; a queue of the same item in the same order with the same configuration needs no new ones. */
    private data class OutroPlan(
      val book: DetailedItem,
      val configuration: AutoSkipConfiguration,
    )

    private class PlantedOutros(
      val plan: OutroPlan,
      val messages: List<PlayerMessage>,
    )
  }
