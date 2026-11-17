package org.grakovne.lissen.ui.screens.player.composable.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import org.grakovne.lissen.domain.SeekTime

fun provideForwardIcon(seekTime: SeekTime) =
  when (SeekTime.snap(seekTime.forward)) {
    5 -> Icons.Rounded.Forward5
    10 -> Icons.Rounded.Forward10
    else -> Icons.Rounded.Forward30
  }
