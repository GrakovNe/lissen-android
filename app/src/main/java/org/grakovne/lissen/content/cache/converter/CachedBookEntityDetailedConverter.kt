package org.grakovne.lissen.content.cache.converter

import org.grakovne.lissen.content.cache.entity.CachedBookEntity
import org.grakovne.lissen.domain.BookChapter
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityDetailedConverter @Inject constructor() {

    fun apply(entity: CachedBookEntity): DetailedItem = DetailedItem(
        id = entity.detailedBook.id,
        title = entity.detailedBook.title,
        author = entity.detailedBook.author,
        libraryId = entity.detailedBook.libraryId,
        files = entity.files.map { fileEntity ->
            BookFile(
                id = fileEntity.bookFileId,
                name = fileEntity.name,
                duration = fileEntity.duration,
                mimeType = fileEntity.mimeType,
            )
        },
        chapters = entity.chapters.map { chapterEntity ->
            BookChapter(
                duration = chapterEntity.duration,
                start = chapterEntity.start,
                end = chapterEntity.end,
                title = chapterEntity.title,
                id = chapterEntity.bookChapterId,
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
