package org.grakovne.lissen.ui.screens.player.composable.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import org.grakovne.lissen.domain.SeekTime

fun provideReplayIcon(seekTime: SeekTime) =
  when (SeekTime.snap(seekTime.rewind)) {
    5 -> Icons.Rounded.Replay5
    10 -> Icons.Rounded.Replay10
    else -> Icons.Rounded.Replay30
  }
