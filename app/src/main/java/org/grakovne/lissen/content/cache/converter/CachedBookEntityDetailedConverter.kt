package org.grakovne.lissen.content.cache.converter

import org.grakovne.lissen.content.cache.entity.CachedBookEntity
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityDetailedConverter @Inject constructor() {

    fun apply(entity: CachedBookEntity): DetailedItem = DetailedItem(
        id = entity.detailedBook.id,
        title = entity.detailedBook.title,
        author = entity.detailedBook.author,
        libraryId = entity.detailedBook.libraryId,
        localProvided = true,
        files = entity.files.map { fileEntity ->
            BookFile(
                id = fileEntity.bookFileId,
                name = fileEntity.name,
                duration = fileEntity.duration,
                mimeType = fileEntity.mimeType,
            )
        },
        chapters = entity.chapters.map { chapterEntity ->
            PlayingChapter(
                duration = chapterEntity.duration,
                start = chapterEntity.start,
                end = chapterEntity.end,
                title = chapterEntity.title,
                available = chapterEntity.isCached,
                id = chapterEntity.bookChapterId,
                podcastEpisodeState = null, // currently state is not available for local mode
            )
        },
        progress = entity.progress?.let { progressEntity ->
            MediaProgress(
                currentTime = progressEntity.currentTime,
                isFinished = progressEntity.isFinished,
                lastUpdate = progressEntity.lastUpdate,
            )
        },
    )
}
