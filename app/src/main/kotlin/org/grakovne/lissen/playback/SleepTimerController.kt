package org.grakovne.lissen.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.playback.service.DefaultTimerActivator
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition

/** How long a sleep timer runs, in seconds of wall-clock time. */
object SleepTimerDelay {
  /**
   * Null when [option] cannot be timed right now: the end of the current chapter is unknown
   * without an item, or when the position is outside its chapters.
   */
  fun of(
    option: TimerOption,
    book: DetailedItem?,
    position: Double,
    speed: Float,
  ): Double? =
    when (option) {
      is DurationTimerOption -> {
        option.duration * 60.0
      }

      is CurrentEpisodeTimerOption -> {
        book
          ?.let { calculateChapterIndexAndPosition(it, position) }
          ?.let { (index, offset) -> book.chapters.getOrNull(index)?.let { chapter -> chapter.duration - offset } }
          ?.let { remaining -> remaining / speed }
      }
    }
}

/**
 * The sleep timer as the listener sees it: the option that is set and the seconds left. The
 * timer itself runs in the service; this side arms and cancels it through commands and
 * mirrors its ticks. A chapter-end timer is re-armed whenever the position or the speed
 * changes, since its length depends on both.
 */
class SleepTimerController(
  private val eventBus: PlaybackEventBus,
  private val defaultTimerActivator: DefaultTimerActivator,
  private val playbackSpeed: () -> Float,
) {
  private val _timerOption = MutableStateFlow<TimerOption?>(null)
  val timerOption: StateFlow<TimerOption?> = _timerOption.asStateFlow()

  private val _timerRemaining = MutableStateFlow<Long?>(null)
  val timerRemaining: StateFlow<Long?> = _timerRemaining.asStateFlow()

  /** Sets (or, with null, cancels) the timer at the listener's request. */
  fun set(
    option: TimerOption?,
    book: DetailedItem?,
    position: Double,
  ) {
    defaultTimerActivator.onTimerManuallySet()
    _timerOption.value = option

    when (option) {
      null -> {
        eventBus.send(PlaybackCommand.CancelTimer)
      }

      else -> {
        SleepTimerDelay
          .of(option, book, position, playbackSpeed())
          ?.let { delay -> eventBus.send(PlaybackCommand.SetTimer(delay, option)) }
      }
    }
  }

  /** The position or the speed changed: a chapter-end timer now ends at another moment. */
  fun adjust(
    book: DetailedItem?,
    position: Double,
  ) {
    when (val option = _timerOption.value) {
      is CurrentEpisodeTimerOption -> {
        set(option, book, position)
      }

      is DurationTimerOption, null -> {}
    }
  }

  /** The first start of playback arms the default timer, if the listener has one. */
  fun onPlaybackStarted(
    book: DetailedItem?,
    position: Double,
  ) {
    defaultTimerActivator.onPlaybackStarted { set(it, book, position) }
  }

  fun onTick(remainingSeconds: Long) {
    _timerRemaining.value = remainingSeconds
  }

  fun onExpired() {
    defaultTimerActivator.onTimerExpired()
    _timerOption.value = null
  }

  /** A fresh item starts without a timer, and with the default one pending again. */
  fun onNewItemPrepared() {
    if (_timerOption.value != null) {
      _timerOption.value = null
      eventBus.send(PlaybackCommand.CancelTimer)
    }

    defaultTimerActivator.onNewBookPrepared()
  }
}
