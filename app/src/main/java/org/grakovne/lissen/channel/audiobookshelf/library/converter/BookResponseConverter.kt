package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorResponse
import org.grakovne.lissen.domain.BookChapter
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookResponseConverter @Inject constructor() {

    fun apply(
        item: BookResponse,
        progressResponse: MediaProgressResponse? = null,
    ): DetailedItem {
        val maybeChapters = item
            .media
            .chapters
            ?.takeIf { it.isNotEmpty() }
            ?.map {
                BookChapter(
                    start = it.start,
                    end = it.end,
                    title = it.title,
                    available = true,
                    id = it.id,
                    duration = it.end - it.start,
                )
            }

        val filesAsChapters: () -> List<BookChapter> = {
            item
                .media
                .tracks
                ?.sortedBy { it.index }
                ?.map { file ->
                    BookChapter(
                        available = true,
                        start = file.startOffset,
                        end = file.startOffset + file.duration,
                        title = file.title.substringBeforeLast("."),
                        id = file.contentUrl.substringAfterLast("/"),
                        duration = file.duration,
                    )
                }
                ?: emptyList()
        }

        return DetailedItem(
            id = item.id,
            title = item.media.metadata.title,
            author = item.media.metadata.authors?.joinToString(", ", transform = LibraryAuthorResponse::name),
            files = item
                .media
                .tracks
                ?.sortedBy { it.index }
                ?.map {
                    BookFile(
                        id = it.contentUrl.substringAfterLast("/"),
                        name = it.title.substringBeforeLast("."),
                        duration = it.duration,
                        mimeType = it.codec,
                    )
                }
                ?: emptyList(),
            chapters = maybeChapters ?: filesAsChapters(),
            libraryId = item.libraryId,
            progress = progressResponse
                ?.let {
                    MediaProgress(
                        currentTime = it.currentTime,
                        isFinished = it.isFinished,
                        lastUpdate = it.lastUpdate,
                    )
                },
        )
    }
}
