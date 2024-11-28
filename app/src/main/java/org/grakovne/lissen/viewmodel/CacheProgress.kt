package org.grakovne.lissen.viewmodel

sealed class CacheProgress {
    data object Caching : CacheProgress()
    data object Completed : CacheProgress()
    data object Removed : CacheProgress()
    data object Error : CacheProgress()
}
