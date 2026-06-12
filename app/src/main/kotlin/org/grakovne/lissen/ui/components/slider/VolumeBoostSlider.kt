package org.grakovne.lissen.ui.components.slider

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

@Composable
fun VolumeBoostSlider(
  db: Int,
  modifier: Modifier = Modifier,
  onUpdate: (Int) -> Unit,
) {
  CommonSlider(
    internalValue = db.coerceIn(MIN_DB, MAX_DB),
    range = MIN_DB..MAX_DB,
    formatHeader = { "${it.roundToInt()} dB" },
    formatIndex = { "$it" },
    modifier = modifier,
    labeledIndexes = labeledIndexes,
    onUpdate = { onUpdate(it.roundToInt().coerceIn(MIN_DB, MAX_DB)) },
  )
}

private const val MIN_DB = 0
private const val MAX_DB = 25

private val labeledIndexes = listOf(0, 5, 10, 15, 20, 25)
