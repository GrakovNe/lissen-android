package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class SeekTime(
  val rewind: Int,
  val forward: Int,
) {
  companion object {
    val Default = SeekTime(rewind = 10, forward = 30)
  }
}
