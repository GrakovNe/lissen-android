package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorResponse
import org.grakovne.lissen.domain.BookAuthor
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.BookSeries
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookResponseConverter
  @Inject
  constructor() {
    fun apply(
      item: BookResponse,
      progressResponse: MediaProgressResponse? = null,
    ): DetailedItem {
      val maybeChapters =
        item
          .media
          .chapters
          ?.takeIf { it.isNotEmpty() }
          ?.mapIndexed { index, chapter ->
            PlayingChapter(
              start = chapter.start,
              end = chapter.end,
              title = chapter.title,
              available = true,
              id = chapter.id,
              duration = chapter.end - chapter.start,
              podcastEpisodeState = null,
              index = index,
            )
          }

      val filesAsChapters: () -> List<PlayingChapter> = {
        item
          .media
          .audioFiles
          ?.sortedBy { it.index }
          ?.foldIndexed(0.0 to mutableListOf<PlayingChapter>()) { index, (accDuration, chapters), file ->
            chapters.add(
              PlayingChapter(
                available = true,
                start = accDuration,
                end = accDuration + (file.duration ?: 0.0),
                title = file.metaTags?.tagTitle ?: file.metadata.filename.removeSuffix(file.metadata.ext),
                duration = file.duration ?: 0.0,
                id = file.ino,
                podcastEpisodeState = null,
                index = index,
                fileName = file.metadata.filename,
              ),
            )
            accDuration + (file.duration ?: 0.0) to chapters
          }?.second
          ?: emptyList()
      }

      return DetailedItem(
        id = item.id,
        title = item.media.metadata.title,
        subtitle = item.media.metadata.subtitle,
        author =
          item.media.metadata.authors
            ?.joinToString(", ", transform = LibraryAuthorResponse::name),
        authors =
          item.media.metadata.authors
            ?.map { BookAuthor(id = it.id, name = it.name) }
            ?: emptyList(),
        narrator =
          item.media.metadata.narrators
            ?.joinToString(separator = ", "),
        files =
          item
            .media
            .audioFiles
            ?.sortedBy { it.index }
            ?.map {
              BookFile(
                id = it.ino,
                name =
                  it.metaTags
                    ?.tagTitle
                    ?: (it.metadata.filename.removeSuffix(it.metadata.ext)),
                duration = it.duration ?: 0.0,
                mimeType = it.mimeType,
                size = it.metadata.size,
              )
            }
            ?: emptyList(),
        chapters = maybeChapters ?: filesAsChapters(),
        libraryId = item.libraryId,
        libraryType = LibraryType.LIBRARY,
        localProvided = false,
        year = item.media.metadata.publishedYear,
        abstract = item.media.metadata.description,
        publisher = item.media.metadata.publisher,
        series =
          item
            .media
            .metadata
            .series
            ?.map {
              BookSeries(
                id = it.id,
                name = it.name,
                serialNumber = it.sequence,
              )
            } ?: emptyList(),
        createdAt = item.addedAt,
        updatedAt = item.ctimeMs,
        progress =
          progressResponse
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
