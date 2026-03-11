package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep

@Keep
data class PlayingItem(
  val id: String,
  val subtitle: String?,
  val series: String?,
  val title: String,
  val author: String?,
)
