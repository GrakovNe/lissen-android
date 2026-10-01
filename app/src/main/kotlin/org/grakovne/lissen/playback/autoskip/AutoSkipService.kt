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
 * Skips the intro and the outro of every chapter, as configured per item. Whatever playback
 * reaches by itself is skipped: a chapter it runs into, the start of a chapter, the position a
 * new queue is placed at, and the "forward" step ([PlaybackSteps]). Where the user seeks to is
 * played as it is. An armed "end of episode" timer takes the outro instead of the skip.
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

    private var owed: Int? = null
    private var planted: PlantedOutros? = null

    // an outro the episode timer is about to pause inside: the skip is due once the player is
    // really paused; a stall that drops isPlaying for a moment must not move on while the timer is active
    private var held: Int? = null

    // the end seek lands slightly short of an outro message clamped to the end of the audio
    // (a chapter with less audio than the server says), which would fire again and again
    private var ended: Int? = null

    private val listener =
      object : Player.Listener {
        override fun onTimelineChanged(
          timeline: Timeline,
          reason: Int,
        ) {
          if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return

          val previous = planted?.plan?.book?.id
          forget()
          planted = null

          // the seek that places the player in the new queue comes later in this same task; a
          // queue rebuilt for the same item (a new episode order) continues where it was
          post {
            plantOutroMessages()
            if (currentBook()?.id != previous) reach(player.currentMediaItemIndex)
          }
        }

        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int,
        ) {
          val another = newPosition.mediaItemIndex != oldPosition.mediaItemIndex

          when (reason) {
            // a file boundary inside a chapter is a transition too
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
              if (another) reach(newPosition.mediaItemIndex)
            }

            Player.DISCONTINUITY_REASON_SEEK -> {
              val step = steps.take(newPosition)
              when (step || (another && newPosition.positionMs == 0L)) {
                true -> reach(newPosition.mediaItemIndex)
                false -> forget()
              }
            }

            else -> {}
          }
        }

        override fun onPlayWhenReadyChanged(
          playWhenReady: Boolean,
          reason: Int,
        ) {
          if (playWhenReady) return

          held?.let { owed = it }
          held = null
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
      forget()
      owed = index
      post { settleOwed() }
    }

    private fun forget() {
      owed = null
      held = null
      ended = null
    }

    private fun settleOwed() {
      val index = owed ?: return
      if (!player.isPlaying) return

      owed = null
      if (player.currentMediaItemIndex != index) return
      val book = plannedBook() ?: return
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

    // the outro of the last chapter is played: ending the item while the user has just pressed
    // play would leave nothing playing
    private fun leaveOutroOnResume(
      book: DetailedItem,
      index: Int,
      configuration: AutoSkipConfiguration,
    ) {
      val exit = AutoSkipPlanner.outroExit(book, index, configuration)
      if (exit is OutroExit.Next) leaveOutro(book, index, exit)
    }

    // the delivered message is the proof: the position is not read again, because it may be slightly short
    private fun onOutroCrossed(index: Int) {
      if (player.currentMediaItemIndex != index || ended == index) return
      val book = plannedBook() ?: return
      val chapter = chapterAt(book, index) ?: return

      when {
        !player.isPlaying -> {
          Timber.d("Auto-skip outro: chapter=$index, takenBy=pause")
          owed = index
        }

        playbackTimer.isEpisodeTimerRunning -> {
          Timber.d("Auto-skip outro: chapter=$index, takenBy=timer")
          held = index
        }

        else -> {
          leaveOutro(book, index, AutoSkipPlanner.outroExit(book, index, chapter.configuration))
        }
      }
    }

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
          // after the seek, whose discontinuity clears everything
          ended = index
        }
      }
    }

    // scheduled again only when the plan changed: a cancelled message stays in the player until
    // it is crossed, and every send sorts the player's message list
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

    // player callbacks arrive inside the call that caused them, so decisions are made afterwards
    private fun post(action: () -> Unit) {
      scope.launch { action() }
    }

    // the queue items have neither the item nor an id (the source factory rebuilds them), so the
    // synced item is matched only by its chapter count
    private fun currentBook(): DetailedItem? = syncState.value.item?.takeIf { it.chapters.size == player.mediaItemCount }

    // the session starts the synchronization of the next item before its queue arrives: until
    // then the queue and its messages still belong to the previous item
    private fun plannedBook(): DetailedItem? = currentBook()?.takeIf { it.id == planted?.plan?.book?.id }

    private fun chapterAt(
      book: DetailedItem,
      index: Int,
    ): SkippableChapter? = book.chapters.getOrNull(index)?.let { preferences.get(book.id).skippable(it.durationMs) }

    private data class OutroPlan(
      val book: DetailedItem,
      val configuration: AutoSkipConfiguration,
    )

    private class PlantedOutros(
      val plan: OutroPlan,
      val messages: List<PlayerMessage>,
    )
  }
