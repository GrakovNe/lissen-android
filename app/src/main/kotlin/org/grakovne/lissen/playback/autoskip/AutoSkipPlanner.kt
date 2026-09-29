package org.grakovne.lissen.playback.autoskip

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter

/** The arithmetic of auto-skip, kept away from the player so it can be checked on a table. */
internal object AutoSkipPlanner {
  /** Where playback goes when the outro of chapter [index] is left: the next chapter on the device past its intro, or the end. */
  fun outroExit(
    book: DetailedItem,
    index: Int,
    configuration: AutoSkipConfiguration,
  ): OutroExit {
    val next = (index + 1..book.chapters.lastIndex).firstOrNull { book.chapters[it].available }

    return when (next) {
      null -> OutroExit.End
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
        book.chapters.mapIndexedNotNull { index, chapter -> configuration.skippable(chapter.durationMs)?.let { index to it.outroStartMs } }
      }

      false -> {
        emptyList()
      }
    }
}

internal sealed interface OutroExit {
  data class Next(
    val index: Int,
    val startMs: Long,
  ) : OutroExit

  data object End : OutroExit
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

/**
 * Where [chapter] ends for playback at [positionSeconds] into it: where its outro begins, unless
 * the position is already inside the outro. That one is the listener's, or one the skip is
 * about to leave, so it runs to the real end. What a timer set to the end of the episode counts to.
 */
fun AutoSkipConfiguration.chapterEndSeconds(
  chapter: PlayingChapter,
  positionSeconds: Double,
): Double {
  val outroStart = skippable(chapter.durationMs)?.outroStartMs?.let { it / MILLIS.toDouble() } ?: chapter.duration

  return if (positionSeconds >= outroStart) chapter.duration else outroStart
}

internal val PlayingChapter.durationMs: Long
  get() = (duration * MILLIS).toLong()

private const val MILLIS = 1000L
