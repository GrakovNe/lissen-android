package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/** Every number of seconds from one up to [maxSeconds], or off at the very left. */
@Composable
fun DisableableTimeSlider(
  context: Context,
  seconds: Int?,
  maxSeconds: Int,
  @PluralsRes secondsLabel: Int,
  @StringRes offLabel: Int,
  modifier: Modifier = Modifier,
  onUpdate: (Int?) -> Unit,
) {
  val labeledIndexes = remember(maxSeconds) { listOf(OFF) + (5..maxSeconds step 5) }

  CommonSlider(
    internalValue = seconds ?: OFF,
    range = OFF..maxSeconds,
    formatHeader = { value ->
      when (val v = value.roundToInt().coerceIn(OFF, maxSeconds)) {
        OFF -> context.getString(offLabel)
        else -> context.resources.getQuantityString(secondsLabel, v, v)
      }
    },
    formatIndex = { if (it == OFF) Icons.Outlined.Close else it },
    modifier = modifier,
    labeledIndexes = labeledIndexes,
    onUpdate = { onUpdate(it.roundToInt().coerceIn(OFF, maxSeconds).takeUnless { v -> v == OFF }) },
  )
}

private const val OFF = 0
