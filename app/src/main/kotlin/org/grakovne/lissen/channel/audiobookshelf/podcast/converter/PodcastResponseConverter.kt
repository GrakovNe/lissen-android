package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
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

/**
 * Maps the server response as is. Episodes keep the server order, recorded in
 * [PlayingChapter.index]; the actual ordering is applied later by
 * [org.grakovne.lissen.content.ordering.ChapterOrdering]. The published date is parsed from
 * `pubDate` with the very same pattern every earlier version used, deliberately: the canonical
 * order derived from it is the coordinate system of every stored progress and bookmark.
 */
@Singleton
class PodcastResponseConverter
  @Inject
  constructor() {
    fun apply(
      item: PodcastResponse,
      progressResponses: List<MediaProgressResponse> = emptyList(),
    ): DetailedItem {
      val episodes = item.media.episodes ?: emptyList()

      // SimpleDateFormat is not thread-safe and podcasts are fetched concurrently: one per call
      val dateFormat = SimpleDateFormat(PUB_DATE_PATTERN, Locale.ENGLISH)

      val totalCurrentTime =
        progressResponses
          .maxByOrNull { it.lastUpdate }
          ?.let { progress ->
            episodes
              .takeWhile { it.id != progress.episodeId }
              .sumOf { it.audioFile.duration ?: 0.0 }
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

      var accumulated = 0.0

      val filesAsChapters =
        episodes.mapIndexed { index, episode ->
          val duration = episode.audioFile.duration ?: 0.0
          val start = accumulated
          accumulated += duration

          PlayingChapter(
            start = start,
            end = accumulated,
            title = episode.title,
            duration = duration,
            id = episode.id,
            available = true,
            podcastEpisodeState =
              progressResponses
                .find { it.episodeId == episode.id }
                ?.let { hasFinished(it) },
            index = index,
            publishedAt = dateFormat.parsePublishedAt(episode.pubDate),
            season = episode.season,
            episode = episode.episode,
            fileName = episode.audioFile.metadata.filename,
          )
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
        files =
          episodes.map {
            BookFile(
              id = it.audioFile.ino,
              name = it.title,
              duration = it.audioFile.duration ?: 0.0,
              mimeType = it.audioFile.mimeType,
              size = it.audioFile.metadata.size,
            )
          },
        chapters = filesAsChapters,
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

    private fun SimpleDateFormat.parsePublishedAt(pubDate: String?): Long? =
      try {
        pubDate?.let { parse(it)?.time }
      } catch (e: Exception) {
        Timber.w("Unable to parse episode pubDate '$pubDate' due to: ${e.message}")
        null
      }

    companion object {
      private const val FINISHED_PROGRESS_THRESHOLD = 0.9
      private const val PUB_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss Z"
    }
  }
