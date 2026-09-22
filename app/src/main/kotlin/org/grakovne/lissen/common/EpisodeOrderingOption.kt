package org.grakovne.lissen.common

import androidx.annotation.Keep

@Keep
enum class EpisodeOrderingOption {
  PUBLISHED_AT,
  TITLE,
  SEASON,
  EPISODE,
  FILE_NAME,
}
