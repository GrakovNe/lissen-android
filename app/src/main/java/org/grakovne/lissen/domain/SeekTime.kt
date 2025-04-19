package org.grakovne.lissen.domain

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
    SEEK_30,
}
