package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class SeekTime(
  val rewind: SeekTimeOption,
  val forward: SeekTimeOption,
) {
  companion object {
    val Default =
      SeekTime(
        rewind = SeekTimeOption.SEEK_10,
        forward = SeekTimeOption.SEEK_30,
      )
  }
}
