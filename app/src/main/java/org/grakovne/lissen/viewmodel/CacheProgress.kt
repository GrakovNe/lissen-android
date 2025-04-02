package org.grakovne.lissen.viewmodel

sealed class CacheProgress {
    data object Idle : CacheProgress()
    data object Caching : CacheProgress()
    data object Completed : CacheProgress()
    data object Error : CacheProgress()
}
