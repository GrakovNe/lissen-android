package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.Author
import org.grakovne.lissen.channel.audiobookshelf.common.model.LibraryItemIdResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.domain.BookChapter
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.domain.MediaProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookResponseConverter @Inject constructor() {

    fun apply(
        item: LibraryItemIdResponse,
        progressResponse: MediaProgressResponse? = null
    ): DetailedBook {
        val maybeChapters = item
            .media
            .chapters
            ?.takeIf { it.isNotEmpty() }
            ?.map {
                BookChapter(
                    start = it.start,
                    end = it.end,
                    title = it.title,
                    id = it.id,
                    duration = it.end - it.start
                )
            }

        val filesAsChapters: () -> List<BookChapter> = {
            item
                .media
                .audioFiles
                ?.sortedBy { it.index }
                ?.fold(0.0 to mutableListOf<BookChapter>()) { (accDuration, chapters), file ->
                    chapters.add(
                        BookChapter(
                            start = accDuration,
                            end = accDuration + file.duration,
                            title = file.metaTags?.tagTitle
                                ?: file.metadata.filename.removeSuffix(file.metadata.ext),
                            duration = file.duration,
                            id = file.ino
                        )
                    )
                    accDuration + file.duration to chapters
                }
                ?.second
                ?: emptyList()
        }

        return DetailedBook(
            id = item.id,
            title = item.media.metadata.title,
            author = item.media.metadata.authors?.joinToString(", ", transform = Author::name),
            files = item
                .media
                .audioFiles
                ?.sortedBy { it.index }
                ?.map {
                    BookFile(
                        id = it.ino,
                        name = it.metaTags
                            ?.tagTitle
                            ?: (it.metadata.filename.removeSuffix(it.metadata.ext)),
                        duration = it.duration,
                        mimeType = it.mimeType
                    )
                }
                ?: emptyList(),
            chapters = maybeChapters ?: filesAsChapters(),
            progress = progressResponse
                ?.let {
                    MediaProgress(
                        currentTime = it.currentTime,
                        isFinished = it.isFinished,
                        lastUpdate = it.lastUpdate
                    )
                }
        )
    }
}
