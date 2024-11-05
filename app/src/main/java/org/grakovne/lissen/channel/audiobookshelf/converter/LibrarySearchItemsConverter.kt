package org.grakovne.lissen.channel.audiobookshelf.converter

import org.grakovne.lissen.channel.audiobookshelf.model.LibraryItem
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookCachedState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySearchItemsConverter @Inject constructor() {
    fun apply(response: List<LibraryItem>): List<Book> {
        return response
            .map {
                Book(
                    id = it.id,
                    title = it.media.metadata.title,
                    author = it.media.metadata.authorName,
                    cachedState = BookCachedState.ABLE_TO_CACHE,
                    duration = it.media.duration.toInt()
                )
            }
    }
}
