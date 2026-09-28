package org.grakovne.lissen.common

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

/**
 * How much of every chapter of an item the player skips on its own: the first [introSeconds]
 * and the last [outroSeconds]. Absent means nothing is skipped.
 */
@Keep
@JsonClass(generateAdapter = true)
data class AutoSkipConfiguration(
  val introSeconds: Int,
  val outroSeconds: Int,
) {
  val enabled: Boolean
    get() = introSeconds > 0 || outroSeconds > 0

  fun sanitized(): AutoSkipConfiguration = copy(introSeconds = introSeconds.coerceAtLeast(0), outroSeconds = outroSeconds.coerceAtLeast(0))

  companion object {
    val disabled = AutoSkipConfiguration(introSeconds = 0, outroSeconds = 0)
  }
}
