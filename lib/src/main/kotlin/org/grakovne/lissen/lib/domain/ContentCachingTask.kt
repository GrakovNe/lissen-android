package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class ContentCachingTask(
  val item: DetailedItem,
  val options: DownloadOption,
  val currentPosition: Double,
) : Serializable
