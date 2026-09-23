package org.grakovne.lissen.common

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

/** Absent means the default order, the one the server has always been shown with. */
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
