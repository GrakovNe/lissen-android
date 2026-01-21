package org.grakovne.lissen.content.cache.persistent

import androidx.annotation.Keep
import org.grakovne.lissen.lib.domain.CacheStatus

@Keep
data class CacheState(
  val status: CacheStatus,
  val progress: Double = 0.0,
)
