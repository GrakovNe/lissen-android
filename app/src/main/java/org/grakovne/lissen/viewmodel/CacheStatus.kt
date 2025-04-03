package org.grakovne.lissen.viewmodel

sealed class CacheStatus {
    data object Idle : CacheStatus()
    data object Caching : CacheStatus()
    data object Completed : CacheStatus()
    data object Error : CacheStatus()
}
