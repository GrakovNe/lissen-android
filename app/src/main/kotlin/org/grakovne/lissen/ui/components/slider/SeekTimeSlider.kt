package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grakovne.lissen.R
import kotlin.math.roundToInt

const val DEFAULT_MIN_SECONDS = 1
const val DEFAULT_MAX_SECONDS = 60

@Composable
fun SeekTimeSlider(
  context: Context,
  seconds: Int,
  modifier: Modifier = Modifier,
  minSeconds: Int = DEFAULT_MIN_SECONDS,
  maxSeconds: Int = DEFAULT_MAX_SECONDS,
  onUpdate: (Int) -> Unit,
) {
  CommonSlider(
    internalValue = seconds.coerceIn(minSeconds, maxSeconds),
    range = minSeconds..maxSeconds,
    formatHeader = { value ->
      val v = value.roundToInt().coerceIn(minSeconds, maxSeconds)
      context.resources.getQuantityString(R.plurals.seek_interval_seconds, v, v)
    },
    formatIndex = { "$it" },
    modifier = modifier,
    labeledIndexes = seekTickIndexes(minSeconds, maxSeconds),
    onUpdate = { onUpdate(it.roundToInt().coerceIn(minSeconds, maxSeconds)) },
  )
}

// Ticks every five seconds, starting at the first multiple of five that is at
// least minSeconds. The default 1..60 range therefore keeps the historical
// 5..60 labels, while a range starting at 10 starts its ticks at 10.
internal fun seekTickIndexes(
  minSeconds: Int,
  maxSeconds: Int,
): List<Int> {
  val firstTick = ((minSeconds + 4) / 5) * 5
  return (firstTick..maxSeconds step 5).toList()
}
