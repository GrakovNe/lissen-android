package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastOrderingRequestConverter @Inject constructor() {

    fun apply(configuration: LibraryOrderingConfiguration): Pair<String, String> {
        val option = when (configuration.option) {
            LibraryOrderingOption.TITLE -> "media.metadata.title"
            LibraryOrderingOption.AUTHOR -> "media.metadata.author"
            LibraryOrderingOption.CREATED_AT -> "addedAt"
        }

        val direction = when (configuration.direction) {
            LibraryOrderingDirection.ASCENDING -> "0"
            LibraryOrderingDirection.DESCENDING -> "1"
        }

        return option to direction
    }
}
