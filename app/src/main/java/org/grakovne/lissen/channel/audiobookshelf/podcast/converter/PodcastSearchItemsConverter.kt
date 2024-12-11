package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItem
import org.grakovne.lissen.domain.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastSearchItemsConverter @Inject constructor() {
    fun apply(response: List<PodcastItem>): List<Book> {
        return response
            .mapNotNull {
                val title = it.media.metadata.title ?: return@mapNotNull null

                val hasMediaItems = (it.media.numAudioFiles ?: 0) > 0
                if (hasMediaItems.not()) {
                    return@mapNotNull null
                }

                Book(
                    id = it.id,
                    title = title,
                    author = it.media.metadata.author,
                    duration = it.media.duration.toInt(),
                )
            }
    }
}
