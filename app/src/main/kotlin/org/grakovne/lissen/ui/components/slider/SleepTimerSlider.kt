package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.LibraryType.LIBRARY
import org.grakovne.lissen.domain.LibraryType.PODCAST
import org.grakovne.lissen.domain.LibraryType.UNKNOWN
import org.grakovne.lissen.domain.TimerOption
import kotlin.math.roundToInt

@Composable
fun SleepTimerSlider(
  context: Context,
  libraryType: LibraryType,
  option: TimerOption?,
  modifier: Modifier = Modifier,
  onUpdate: (TimerOption?) -> Unit,
) {
  val floatRange = INTERNAL_MIN_VALUE.toFloat()..INTERNAL_MAX_VALUE.toFloat()

  CommonSlider(
    internalValue = option.toInternalValue(),
    range = INTERNAL_MIN_VALUE..INTERNAL_MAX_VALUE,
    formatHeader = { it.coerceIn(floatRange).toLabelText(libraryType, context) },
    formatIndex = { it.toLabelIcon() },
    modifier = modifier,
    labeledIndexes = labeledIndexes,
    onUpdate = { sliderValue -> onUpdate(sliderValue.coerceIn(floatRange).toTimerOption()) },
  )
}

private fun TimerOption?.toInternalValue(): Int =
  when (this) {
    null -> INTERNAL_DISABLED
    is DurationTimerOption -> duration.coerceIn(1, INTERNAL_MAX_VALUE)
    CurrentEpisodeTimerOption -> INTERNAL_CHAPTER_END
  }

private fun Float.toTimerOption(): TimerOption? {
  val value = roundToInt().coerceIn(INTERNAL_MIN_VALUE, INTERNAL_MAX_VALUE)

  return when (value) {
    INTERNAL_DISABLED -> null
    INTERNAL_CHAPTER_END -> CurrentEpisodeTimerOption
    else -> DurationTimerOption(value)
  }
}

private fun Float.toLabelText(
  libraryType: LibraryType,
  context: Context,
): String {
  val value = roundToInt().coerceIn(INTERNAL_MIN_VALUE, INTERNAL_MAX_VALUE)

  return when (value) {
    INTERNAL_DISABLED -> {
      context.getString(R.string.timer_option_disabled)
    }

    INTERNAL_CHAPTER_END -> {
      when (libraryType) {
        LIBRARY -> context.getString(R.string.timer_option_after_current_chapter)
        PODCAST, UNKNOWN -> context.getString(R.string.timer_option_after_current_episode)
      }
    }

    else -> {
      context.resources.getQuantityString(R.plurals.timer_option_after_time, value, value)
    }
  }
}

private fun Int.toLabelIcon(): Any =
  when (this) {
    INTERNAL_DISABLED -> Icons.Outlined.Close
    INTERNAL_CHAPTER_END -> Icons.Outlined.MusicNote
    else -> this
  }

private const val INTERNAL_MIN_VALUE = -1
private const val INTERNAL_MAX_VALUE = 120
private const val INTERNAL_DISABLED = 0
private const val INTERNAL_CHAPTER_END = -1

private val labeledIndexes =
  listOf(INTERNAL_CHAPTER_END, INTERNAL_DISABLED) + (5..INTERNAL_MAX_VALUE step 5)
