package org.grakovne.lissen.domain

import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class ContentCachingTask(
  val itemId: String,
  val options: DownloadOption,
  val currentPosition: Double,
) : Serializable
