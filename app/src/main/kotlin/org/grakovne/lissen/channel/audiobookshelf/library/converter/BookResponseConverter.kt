package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorResponse
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.BookSeries
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.MediaProgress
import org.grakovne.lissen.lib.domain.PlayingChapter
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
          ?.map {
            PlayingChapter(
              start = it.start,
              end = it.end,
              title = it.title,
              available = true,
              id = it.id,
              duration = it.end - it.start,
              podcastEpisodeState = null,
            )
          }

      val filesAsChapters: () -> List<PlayingChapter> = {
        item
          .media
          .audioFiles
          ?.sortedBy { it.index }
          ?.fold(0.0 to mutableListOf<PlayingChapter>()) { (accDuration, chapters), file ->
            chapters.add(
              PlayingChapter(
                available = true,
                start = accDuration,
                end = accDuration + file.duration,
                title = file.metaTags?.tagTitle ?: file.metadata.filename.removeSuffix(file.metadata.ext),
                duration = file.duration,
                id = file.ino,
                podcastEpisodeState = null,
              ),
            )
            accDuration + file.duration to chapters
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
                duration = it.duration,
                mimeType = it.mimeType,
              )
            }
            ?: emptyList(),
        chapters = maybeChapters ?: filesAsChapters(),
        libraryId = item.libraryId,
        localProvided = false,
        year = extractYear(item.media.metadata.publishedYear),
        abstract = item.media.metadata.description,
        publisher = item.media.metadata.publisher,
        series =
          item
            .media
            .metadata
            .series
            ?.map {
              BookSeries(
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

    private fun extractYear(rawYear: String?): String? {
      if (rawYear.isNullOrBlank()) {
        return null
      }

      // 1. If it's explicitly 4 digits, assume it's a year
      if (rawYear.matches(Regex("^\\d{4}$"))) {
        return rawYear
      }

      return try {
        // 2. Try parsing as ZonedDateTime (ISO 8601 with timezone, e.g. 2010-10-07T07:13:01Z)
        ZonedDateTime.parse(rawYear).year.toString()
      } catch (e: Exception) {
        try {
          // 3. Try parsing as LocalDate (yyyy-MM-dd)
          LocalDate.parse(rawYear).year.toString()
        } catch (e: Exception) {
          // 4. Fallback: If it starts with 4 digits, take them
          Regex("^(\\d{4})").find(rawYear)?.groupValues?.get(1) ?: rawYear
        }
      }
    }
  }
