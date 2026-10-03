package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/** A number of seconds, or off at the very left. */
@Composable
fun SecondsOrOffSlider(
  context: Context,
  seconds: Int?,
  @PluralsRes secondsLabel: Int,
  @StringRes offLabel: Int,
  modifier: Modifier = Modifier,
  onUpdate: (Int?) -> Unit,
) {
  CommonSlider(
    internalValue = seconds ?: OFF,
    range = OFF..MAX_SECONDS,
    formatHeader = { value ->
      when (val v = value.roundToInt().coerceIn(OFF, MAX_SECONDS)) {
        OFF -> context.getString(offLabel)
        else -> context.resources.getQuantityString(secondsLabel, v, v)
      }
    },
    formatIndex = { if (it == OFF) Icons.Outlined.Close else it },
    modifier = modifier,
    labeledIndexes = labeledIndexes,
    onUpdate = { onUpdate(it.roundToInt().coerceIn(OFF, MAX_SECONDS).takeUnless { v -> v == OFF }) },
  )
}

private const val OFF = 0
private const val MAX_SECONDS = 60

private val labeledIndexes = listOf(OFF) + (5..MAX_SECONDS step 5)
