package org.grakovne.lissen.viewmodel

data class CacheState(
    val status: CacheStatus,
    val progress: Double = 0.0,
)
