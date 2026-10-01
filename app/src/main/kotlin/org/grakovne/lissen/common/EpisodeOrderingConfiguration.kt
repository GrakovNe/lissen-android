package org.grakovne.lissen.common

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

/** Absent means the default order, which the server has always received. */
@Keep
@JsonClass(generateAdapter = true)
data class EpisodeOrderingConfiguration(
  val option: EpisodeOrderingOption,
  val direction: LibraryOrderingDirection,
) {
  companion object {
    val default =
      EpisodeOrderingConfiguration(
        option = EpisodeOrderingOption.PUBLISHED_AT,
        direction = LibraryOrderingDirection.ASCENDING,
      )
  }
}
