package org.grakovne.lissen.lib.domain

sealed class CacheStatus {
  data object Idle : CacheStatus()

  data object Caching : CacheStatus()

  data object Completed : CacheStatus()

  data object Error : CacheStatus()
}
