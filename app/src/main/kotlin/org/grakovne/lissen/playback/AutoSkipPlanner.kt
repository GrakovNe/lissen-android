package org.grakovne.lissen.playback

import org.grakovne.lissen.common.AutoSkipConfiguration
import org.grakovne.lissen.domain.DetailedItem

/** The arithmetic and the rules of auto-skip, kept away from the player so they can be checked on a table. */
internal object AutoSkipPlanner {
  /** A seek this close to the start of a chapter is taken as landing at its start. */
  const val CHAPTER_ENTRY_TOLERANCE_MS = 500L

  /** A restart from within this far past the intro is a "previous" press, not a return to the chapter. */
  const val RESTART_TOLERANCE_MS = 3_500L

  /**
   * What a seek from ([fromIndex], [fromMs]) to ([toIndex], [toMs]) means for the chapter it
   * lands in. Another chapter's start is entered. The start of the same chapter is entered only
   * from well beyond its intro, so a rewind that touches the start, or a "previous" that restarts
   * the chapter from just past the intro, plays the chapter as it is. Moving back into an outro
   * keeps it, moving forward inside one changes nothing. Anything else is neutral.
   */
  fun landing(
    fromIndex: Int,
    fromMs: Long,
    toIndex: Int,
    toMs: Long,
    chapter: SkippableChapter?,
  ): SeekLanding {
    val atStart = toMs < CHAPTER_ENTRY_TOLERANCE_MS
    val backwards = toIndex < fromIndex || (toIndex == fromIndex && toMs < fromMs)
    val inOutro = chapter?.outroReached(toMs) == true

    return when {
      atStart && toIndex != fromIndex -> SeekLanding.ENTRY
      atStart && chapter != null && fromMs > chapter.introEndMs + RESTART_TOLERANCE_MS -> SeekLanding.ENTRY
      atStart -> SeekLanding.START_KEPT
      inOutro && backwards -> SeekLanding.OUTRO_KEPT
      inOutro -> SeekLanding.OUTRO_FORWARD
      else -> SeekLanding.ELSEWHERE
    }
  }

  /** Where playback goes when the outro of chapter [index] is left: the next chapter on the device past its intro, or the end. */
  fun outroExit(
    book: DetailedItem,
    index: Int,
    configuration: AutoSkipConfiguration,
  ): OutroExit {
    val next = (index + 1..book.chapters.lastIndex).firstOrNull { book.chapters[it].available }

    return when (next) {
      null -> OutroExit.End(book.chapters[index].durationMs)
      else -> OutroExit.Next(next, configuration.skippable(book.chapters[next].durationMs)?.introTargetMs(0L) ?: 0L)
    }
  }

  /** The chapters of [book] with an outro to skip, and where each outro begins. */
  fun outroPositions(
    book: DetailedItem,
    configuration: AutoSkipConfiguration,
  ): List<Pair<Int, Long>> =
    when (configuration.outroSeconds > 0) {
      true -> {
        book.chapters.mapIndexedNotNull { index, chapter ->
          configuration.skippable(chapter.durationMs)?.let {
            index to
              it.outroStartMs
          }
        }
      }

      false -> {
        emptyList()
      }
    }
}

internal enum class SeekLanding {
  /** The chapter is entered at its start: its intro is skipped once playback runs. */
  ENTRY,

  /** The start of the chapter is meant, intro included. */
  START_KEPT,

  /** The listener moved back into the outro on purpose: it is played. */
  OUTRO_KEPT,

  /** The listener moved forward into the outro: a kept one stays kept, an unclaimed one is skipped once playback runs. */
  OUTRO_FORWARD,

  /** Somewhere in the middle: nothing pending, nothing kept. */
  ELSEWHERE,
}

internal sealed interface OutroExit {
  data class Next(
    val index: Int,
    val startMs: Long,
  ) : OutroExit

  data class End(
    val atMs: Long,
  ) : OutroExit
}

/** A chapter of [durationMs] with a configuration that leaves something of it. */
internal data class SkippableChapter(
  val configuration: AutoSkipConfiguration,
  val durationMs: Long,
) {
  val introEndMs: Long
    get() = configuration.introSeconds * MILLIS

  val outroStartMs: Long
    get() = durationMs - configuration.outroSeconds * MILLIS

  /** Where the chapter entered at [positionMs] should really start, or null when it is fine where it is. */
  fun introTargetMs(positionMs: Long): Long? = introEndMs.takeIf { configuration.introSeconds > 0 && positionMs < it }

  /** True once playback at [positionMs] has entered the part of the chapter that is skipped. */
  fun outroReached(positionMs: Long): Boolean = configuration.outroSeconds > 0 && positionMs >= outroStartMs
}

/** The configuration as it applies to a chapter of [durationMs]: nothing when there is nothing to skip or the skips would swallow the chapter. */
internal fun AutoSkipConfiguration.skippable(durationMs: Long): SkippableChapter? =
  takeIf { enabled && durationMs > 0L && (introSeconds + outroSeconds) * MILLIS < durationMs }?.let { SkippableChapter(it, durationMs) }

private const val MILLIS = 1000L
