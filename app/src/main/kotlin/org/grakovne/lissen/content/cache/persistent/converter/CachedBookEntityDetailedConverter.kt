package org.grakovne.lissen.content.cache.persistent.converter

import com.squareup.moshi.Types
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.content.cache.persistent.entity.BookAuthorDto
import org.grakovne.lissen.content.cache.persistent.entity.BookSeriesDto
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity
import org.grakovne.lissen.domain.BookAuthor
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.BookSeries
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityDetailedConverter
  @Inject
  constructor(
    private val mediaProgressEntityConverter: MediaProgressEntityConverter,
  ) {
    fun apply(entity: CachedBookEntity): DetailedItem =
      DetailedItem(
        id = entity.detailedBook.id,
        title = entity.detailedBook.title,
        subtitle = entity.detailedBook.subtitle,
        author = entity.detailedBook.author,
        authors =
          entity
            .detailedBook
            .authorsJson
            ?.let { authorsAdapter.fromJson(it) }
            ?.map { BookAuthor(id = it.id, name = it.name) }
            ?: emptyList(),
        narrator = entity.detailedBook.narrator,
        libraryId = entity.detailedBook.libraryId,
        localProvided = true,
        files =
          entity.files.map { fileEntity ->
            BookFile(
              id = fileEntity.bookFileId,
              name = fileEntity.name,
              size = fileEntity.size,
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
            ?.let { seriesAdapter.fromJson(it) }
            ?.map {
              BookSeries(
                id = it.id,
                name = it.title,
                serialNumber = it.sequence,
              )
            } ?: emptyList(),
        progress = entity.progress?.let { mediaProgressEntityConverter.apply(it) },
      )

    private companion object {
      val authorsType = Types.newParameterizedType(List::class.java, BookAuthorDto::class.java)
      val authorsAdapter = moshi.adapter<List<BookAuthorDto>>(authorsType)
      val seriesType = Types.newParameterizedType(List::class.java, BookSeriesDto::class.java)
      val seriesAdapter = moshi.adapter<List<BookSeriesDto>>(seriesType)
    }
  }
