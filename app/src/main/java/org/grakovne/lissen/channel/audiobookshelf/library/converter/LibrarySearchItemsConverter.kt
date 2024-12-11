package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.domain.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySearchItemsConverter @Inject constructor() {
    fun apply(response: List<LibraryItem>) = response
        .mapNotNull {
            val title = it.media.metadata.title ?: return@mapNotNull null

            val hasMediaItems = (it.media.numAudioFiles ?: 0) > 0
            if (hasMediaItems.not()) {
                return@mapNotNull null
            }

            Book(
                id = it.id,
                title = title,
                author = it.media.metadata.authorName,
                duration = it.media.duration.toInt(),
            )
        }
}
