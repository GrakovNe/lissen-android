package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.podcast.PodcastItem
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookCachedState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastSearchItemsConverter @Inject constructor() {
    fun apply(response: List<PodcastItem>): List<Book> {
        return response
            .mapNotNull {
                val title = it.media.metadata.title ?: return@mapNotNull null

                Book(
                    id = it.id,
                    title = title,
                    author = it.media.metadata.author,
                    cachedState = BookCachedState.ABLE_TO_CACHE,
                    duration = it.media.duration.toInt()
                )
            }
    }
}
