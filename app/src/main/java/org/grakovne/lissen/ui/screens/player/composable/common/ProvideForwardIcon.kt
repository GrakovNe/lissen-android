package org.grakovne.lissen.ui.screens.player.composable.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.SeekTimeOption

fun provideForwardIcon(seekTime: SeekTime) =
    when (seekTime.forward) {
        SeekTimeOption.SEEK_5 -> Icons.Rounded.Forward5
        SeekTimeOption.SEEK_10 -> Icons.Rounded.Forward10
        SeekTimeOption.SEEK_30 -> Icons.Rounded.Forward30
    }
