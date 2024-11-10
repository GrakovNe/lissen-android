package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.LibraryResponse
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.domain.Library
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryResponseConverter @Inject constructor() {

    fun apply(response: LibraryResponse): List<Library> = response
        .libraries
        .mapNotNull {
            it
                .mediaType
                .toLibraryType()
                ?.let { type -> Library(it.id, it.name, type) }
        }

    private fun String.toLibraryType() = when (this) {
        "podcast" -> LibraryType.AUDIOBOOKSHELF_PODCAST
        "book" -> LibraryType.AUDIOBOOKSHELF_LIBRARY
        else -> null
    }
}
