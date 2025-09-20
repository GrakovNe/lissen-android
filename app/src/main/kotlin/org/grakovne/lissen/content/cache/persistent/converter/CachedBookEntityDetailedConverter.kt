package org.grakovne.lissen.content.cache.persistent.converter

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.grakovne.lissen.content.cache.persistent.entity.BookSeriesDto
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.BookSeries
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.MediaProgress
import org.grakovne.lissen.lib.domain.PlayingChapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityDetailedConverter
  @Inject
  constructor() {
    fun apply(entity: CachedBookEntity): DetailedItem =
      DetailedItem(
        id = entity.detailedBook.id,
        title = entity.detailedBook.title,
        subtitle = entity.detailedBook.subtitle,
        author = entity.detailedBook.author,
        narrator = entity.detailedBook.narrator,
        libraryId = entity.detailedBook.libraryId,
        localProvided = true,
        files =
          entity.files.map { fileEntity ->
            BookFile(
              id = fileEntity.bookFileId,
              name = fileEntity.name,
              duration = fileEntity.duration,
              mimeType = fileEntity.mimeType,
            )
          },
        chapters =
          entity.chapters.map { chapterEntity ->
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
        abstract = entity.detailedBook.abstract,
        publisher = entity.detailedBook.publisher,
        year = entity.detailedBook.year,
        createdAt = entity.detailedBook.createdAt,
        updatedAt = entity.detailedBook.updatedAt,
        series =
          entity
            .detailedBook
            .seriesJson
            ?.let {
              val type = object : TypeToken<List<BookSeriesDto>>() {}.type
              gson.fromJson<List<BookSeriesDto>>(it, type)
            }?.map {
              BookSeries(
                name = it.title,
                serialNumber = it.sequence,
              )
            } ?: emptyList(),
        progress =
          entity.progress?.let { progressEntity ->
            MediaProgress(
              currentTime = progressEntity.currentTime,
              isFinished = progressEntity.isFinished,
              lastUpdate = progressEntity.lastUpdate,
            )
          },
      )

    companion object {
      val gson = Gson()
    }
  }
