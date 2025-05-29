package org.grakovne.lissen.domain

import androidx.annotation.Keep
import org.grakovne.lissen.channel.common.LibraryType

@Keep
data class Library(
    val id: String,
    val title: String,
    val type: LibraryType,
)
