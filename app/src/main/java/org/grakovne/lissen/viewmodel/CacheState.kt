package org.grakovne.lissen.viewmodel

import org.grakovne.lissen.domain.CacheStatus

data class CacheState(
    val status: CacheStatus,
    val progress: Double = 0.0,
)
