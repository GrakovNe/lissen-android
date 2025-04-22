package org.grakovne.lissen.content.cache

import androidx.annotation.Keep
import org.grakovne.lissen.domain.CacheStatus

@Keep
data class CacheState(
    val status: CacheStatus,
    val progress: Double = 0.0,
)
