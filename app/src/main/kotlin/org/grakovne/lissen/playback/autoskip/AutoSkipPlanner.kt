package org.grakovne.lissen.playback.autoskip

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter

internal object AutoSkipPlanner {
  fun outroExit(
    book: DetailedItem,
    index: Int,
    configuration: AutoSkipConfiguration,
  ): OutroExit {
    val next = (index + 1..book.chapters.lastIndex).firstOrNull { book.chapters[it].available }

    return when (next) {
      null -> OutroExit.End
      else -> OutroExit.Next(next, configuration.skippable(book.chapters[next].durationMs)?.introEndMs ?: 0L)
    }
  }

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

internal data class SkippableChapter(
  val configuration: AutoSkipConfiguration,
  val durationMs: Long,
) {
  val introEndMs: Long
    get() = configuration.introSeconds * MILLIS

  val outroStartMs: Long
    get() = durationMs - configuration.outroSeconds * MILLIS

  fun introTargetMs(positionMs: Long): Long? = introEndMs.takeIf { configuration.introSeconds > 0 && positionMs < it }

  fun outroReached(positionMs: Long): Boolean = configuration.outroSeconds > 0 && positionMs >= outroStartMs
}

/** Null when there is nothing to skip, or the skips would swallow the whole chapter. */
internal fun AutoSkipConfiguration.skippable(durationMs: Long): SkippableChapter? =
  takeIf { enabled && durationMs > 0L && (introSeconds + outroSeconds) * MILLIS < durationMs }?.let { SkippableChapter(it, durationMs) }

/**
 * Where an "end of episode" timer stops: where the outro begins, or the real end once playback is
 * inside the outro already (the listener's own, or one about to be left).
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
