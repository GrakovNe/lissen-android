package org.grakovne.lissen.channel.audiobookshelf.converter

import org.grakovne.lissen.channel.audiobookshelf.model.LibrarySearchResponse
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookCachedState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySearchItemResponseConverter @Inject constructor() {

    fun apply(response: LibrarySearchResponse): List<Book> = response
        .book
        .map {
            Book(
                id = it.libraryItem.id,
                title = it.libraryItem.media.metadata.title,
                author = it.libraryItem.media.metadata.authorName,
                cachedState = BookCachedState.ABLE_TO_CACHE,
                duration = it.libraryItem.media.duration.toInt()
            )
        }
}
