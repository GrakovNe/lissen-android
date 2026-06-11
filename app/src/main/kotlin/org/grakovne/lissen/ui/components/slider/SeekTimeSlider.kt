package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grakovne.lissen.R
import kotlin.math.roundToInt

@Composable
fun SeekTimeSlider(
  context: Context,
  seconds: Int,
  modifier: Modifier = Modifier,
  onUpdate: (Int) -> Unit,
) {
  CommonSlider(
    internalValue = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS),
    range = MIN_SECONDS..MAX_SECONDS,
    formatHeader = { value ->
      val v = value.roundToInt().coerceIn(MIN_SECONDS, MAX_SECONDS)
      context.resources.getQuantityString(R.plurals.seek_interval_seconds, v, v)
    },
    formatIndex = { "$it" },
    modifier = modifier,
    labeledIndexes = labeledIndexes,
    onUpdate = { onUpdate(it.roundToInt().coerceIn(MIN_SECONDS, MAX_SECONDS)) },
  )
}

private const val MIN_SECONDS = 1
private const val MAX_SECONDS = 60

private val labeledIndexes = listOf(1, 5, 10, 15, 20, 30, 45, 60)
