package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
data class SeekTime(
    val rewind: SeekTimeOption,
    val forward: SeekTimeOption,
) {
    companion object {
        val Default = SeekTime(
            rewind = SeekTimeOption.SEEK_10,
            forward = SeekTimeOption.SEEK_30,
        )
    }
}

enum class SeekTimeOption {
    SEEK_5,
    SEEK_10,
    SEEK_15,
    SEEK_30,
    SEEK_60,
}
