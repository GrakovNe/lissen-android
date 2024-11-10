package org.grakovne.lissen.domain

import org.grakovne.lissen.channel.common.LibraryType

data class Library(
    val id: String,
    val title: String,
    val type: LibraryType
)
