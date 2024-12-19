package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.PagedItems
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryPageResponseConverter @Inject constructor() {

    fun apply(response: LibraryItemsResponse): PagedItems<Book> = response
        .results
        .mapNotNull {
            val title = it.media.metadata.title ?: return@mapNotNull null

            val hasMediaItems = it.media.numAudioFiles?.let { it > 0 } ?: true
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
        .let {
            PagedItems(
                items = it,
                currentPage = response.page,
            )
        }
}
