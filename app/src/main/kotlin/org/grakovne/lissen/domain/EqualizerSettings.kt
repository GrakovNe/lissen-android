package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class EqualizerSettings(
  val gains: List<Int>,
) {
  val isActive: Boolean
    get() = gains.any { it != 0 }

  companion object {
    /**
     * The app drives a fixed band set, so the domain owns the band count. The centre and cutoff
     * frequencies for those bands live in DynamicsProcessingTuning.
     */
    const val BAND_COUNT = 5

    val Default = EqualizerSettings(gains = emptyList())
  }
}
