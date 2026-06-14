package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grakovne.lissen.R
import kotlin.math.roundToInt

@Composable
fun ConnectTimeoutSlider(
  context: Context,
  seconds: Int,
  modifier: Modifier = Modifier,
  onUpdate: (Int) -> Unit,
) {
  CommonSlider(
    internalValue = seconds.coerceIn(MIN_CONNECT_SECONDS, MAX_CONNECT_SECONDS),
    range = MIN_CONNECT_SECONDS..MAX_CONNECT_SECONDS,
    formatHeader = { value ->
      val v = value.roundToInt().coerceIn(MIN_CONNECT_SECONDS, MAX_CONNECT_SECONDS)
      context.resources.getQuantityString(R.plurals.seek_interval_seconds, v, v)
    },
    formatIndex = { "$it" },
    modifier = modifier,
    labeledIndexes = CONNECT_LABELED_INDEXES,
    onUpdate = { onUpdate(it.roundToInt().coerceIn(MIN_CONNECT_SECONDS, MAX_CONNECT_SECONDS)) },
  )
}

@Composable
fun ReadTimeoutSlider(
  context: Context,
  seconds: Int,
  modifier: Modifier = Modifier,
  onUpdate: (Int) -> Unit,
) {
  CommonSlider(
    internalValue = seconds.coerceIn(MIN_READ_SECONDS, MAX_READ_SECONDS),
    range = MIN_READ_SECONDS..MAX_READ_SECONDS,
    formatHeader = { value ->
      val v = value.roundToInt().coerceIn(MIN_READ_SECONDS, MAX_READ_SECONDS)
      context.resources.getQuantityString(R.plurals.seek_interval_seconds, v, v)
    },
    formatIndex = { "$it" },
    modifier = modifier,
    labeledIndexes = READ_LABELED_INDEXES,
    onUpdate = { onUpdate(it.roundToInt().coerceIn(MIN_READ_SECONDS, MAX_READ_SECONDS)) },
  )
}

private const val MIN_CONNECT_SECONDS = 5
private const val MAX_CONNECT_SECONDS = 120

private const val MIN_READ_SECONDS = 15
private const val MAX_READ_SECONDS = 300

private val CONNECT_LABELED_INDEXES = listOf(5, 15, 30, 60, 90, 120)
private val READ_LABELED_INDEXES = listOf(15, 30, 60, 90, 120, 180, 300)
