package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryOrderingRequestConverter @Inject constructor() {

    fun apply(configuration: LibraryOrderingConfiguration): String = when (configuration.option) {
        LibraryOrderingOption.TITLE -> "media.metadata.title"
        LibraryOrderingOption.AUTHOR -> "media.metadata.authorName"
        LibraryOrderingOption.PUBLISHED_YEAR -> "media.metadata.publishedYear"
        LibraryOrderingOption.CREATED_AT -> "addedAt"
        LibraryOrderingOption.DURATION -> "media.duration"
        LibraryOrderingOption.MODIFIED_AT -> "mtimeMs"
        LibraryOrderingOption.CHAPTERS_COUNT -> "media.numTracks"
    }
}
