package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.podcast.PodcastItemsResponse
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookCachedState
import org.grakovne.lissen.domain.PagedItems
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastPageResponseConverter @Inject constructor() {

    fun apply(response: PodcastItemsResponse): PagedItems<Book> = response
        .results
        .mapNotNull {
            val title = it.media.metadata.title ?: return@mapNotNull null

            Book(
                id = it.id,
                title = title,
                author = it.media.metadata.author,
                cachedState = BookCachedState.UNABLE_TO_CACHE,
                duration = it.media.duration.toInt()
            )
        }
        .let {
            PagedItems(
                items = it,
                currentPage = response.page
            )
        }
}
