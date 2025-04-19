package org.grakovne.lissen.ui.screens.player.composable.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.SeekTimeOption

fun provideReplayIcon(seekTime: SeekTime) =
    when (seekTime.rewind) {
        SeekTimeOption.SEEK_5 -> Icons.Rounded.Replay5
        SeekTimeOption.SEEK_10 -> Icons.Rounded.Replay10
        SeekTimeOption.SEEK_30 -> Icons.Rounded.Replay30
    }
