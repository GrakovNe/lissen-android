package org.grakovne.lissen.playback.autoskip

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class AutoSkipConfiguration(
  val introSeconds: Int,
  val outroSeconds: Int,
) {
  val enabled: Boolean
    get() = introSeconds > 0 || outroSeconds > 0

  companion object {
    val disabled = AutoSkipConfiguration(introSeconds = 0, outroSeconds = 0)
  }
}
