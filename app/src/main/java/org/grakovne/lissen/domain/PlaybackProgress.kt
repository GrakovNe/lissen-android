package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
data class PlaybackProgress(
    val currentTime: Double,
    val totalTime: Double,
)
