package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastEpisodeResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.common.EpisodeOrdering
import org.grakovne.lissen.common.EpisodeOrderingEngine
import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastResponseConverter
  @Inject
  constructor() {
    fun apply(
      item: PodcastResponse,
      progressResponses: List<MediaProgressResponse> = emptyList(),
    ): DetailedItem {
      val rawChapters: List<PlayingChapter> =
        item
          .media
          .episodes
          ?.map { episode ->
            PlayingChapter(
              start = 0.0,
              end = episode.audioFile.duration ?: 0.0,
              title = episode.title,
              duration = episode.audioFile.duration ?: 0.0,
              id = episode.id,
              available = true,
              podcastEpisodeState =
                progressResponses
                  .find { it.episodeId == episode.id }
                  ?.let { hasFinished(it) },
              publishedAt = episode.pubDate.toEpochMillis(),
              season = episode.season.safeToInt(),
              episodeNumber = episode.episode.safeToInt(),
              filename = episode.audioFile.metadata.filename,
            )
          }
          ?: emptyList()

      val chapters = EpisodeOrderingEngine.applyOrdering(rawChapters, EpisodeOrdering.DEFAULT)

      val totalCurrentTime =
        progressResponses
          .maxByOrNull { it.lastUpdate }
          ?.let { progress ->
            chapters
              .takeWhile { it.id != progress.episodeId }
              .sumOf { it.duration }
              .plus(progress.currentTime)
          }

      val latestEpisodeMediaProgress =
        progressResponses
          .maxByOrNull { it.lastUpdate }
          ?.let {
            MediaProgress(
              currentTime = totalCurrentTime ?: 0.0,
              isFinished = it.isFinished,
              lastUpdate = it.lastUpdate,
            )
          }

      val episodesById = item.media.episodes?.associateBy { it.id } ?: emptyMap()

      val files =
        chapters.mapNotNull { chapter ->
          episodesById[chapter.id]?.let { episode ->
            BookFile(
              id = episode.audioFile.ino,
              name = episode.title,
              duration = episode.audioFile.duration ?: 0.0,
              mimeType = episode.audioFile.mimeType,
              size = episode.audioFile.metadata.size,
            )
          }
        }

      return DetailedItem(
        id = item.id,
        title = item.media.metadata.title,
        subtitle = null,
        libraryId = item.libraryId,
        libraryType = LibraryType.PODCAST,
        author = item.media.metadata.author,
        narrator = null,
        localProvided = false,
        files = files,
        chapters = chapters,
        progress = latestEpisodeMediaProgress,
        year = null, // we have no "Year" for the ongoing media
        abstract = item.media.metadata.description,
        publisher = item.media.metadata.publisher,
        series = emptyList(), // there is no series for podcast
        createdAt = item.addedAt,
        updatedAt = item.ctimeMs,
      )
    }

    private fun hasFinished(progress: MediaProgressResponse): BookChapterState? =
      when (progress.isFinished || progress.progress > FINISHED_PROGRESS_THRESHOLD) {
        true -> BookChapterState.FINISHED
        false -> null
      }

    companion object {
      private const val FINISHED_PROGRESS_THRESHOLD = 0.9
      private const val PUB_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss Z"

      // SimpleDateFormat is not thread-safe and podcast books are fetched concurrently,
      // so parsing happens with a dedicated instance per conversion instead of a shared one.
      private fun String?.toEpochMillis(): Long? {
        val value = this ?: return null

        return try {
          SimpleDateFormat(PUB_DATE_PATTERN, Locale.ENGLISH).parse(value)?.time
        } catch (e: Exception) {
          Timber.w("Unable to parse episode pubDate '$value' due to: ${e.message}")
          null
        }
      }

      private fun String?.safeToInt(): Int? {
        val maybeNumber = this?.takeIf { it.isNotBlank() }

        return try {
          maybeNumber?.toInt()
        } catch (ex: Exception) {
          Timber.w("Unable to parse '$maybeNumber' as season/episode number due to: ${ex.message}")
          null
        }
      }
    }
  }
