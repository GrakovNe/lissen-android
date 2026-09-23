package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.content.ordering.ChapterLocation
import org.grakovne.lissen.content.ordering.ChapterOrdering
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
 * Maps into the canonical order, see [ChapterOrdering]. pubDate keeps the pattern every earlier
 * version used: stored positions are expressed in the order derived from it.
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

      // SimpleDateFormat is not thread-safe: one per call
      val dateFormat = SimpleDateFormat(PUB_DATE_PATTERN, Locale.ENGLISH)

      val latestProgress = progressResponses.maxByOrNull { it.lastUpdate }

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

      val raw =
        DetailedItem(
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
          progress = null,
          year = null, // we have no "Year" for the ongoing media
          abstract = item.media.metadata.description,
          publisher = item.media.metadata.publisher,
          series = emptyList(), // there is no series for podcast
          createdAt = item.addedAt,
          updatedAt = item.ctimeMs,
        )

      // a finished episode arrives with currentTime on its end, which in another order is the start
      // of an unrelated episode: anchor it in the canonical timeline first
      val canonical = ChapterOrdering.canonical(raw)

      return canonical.copy(progress = latestProgress?.let { canonical.anchoredProgress(it) })
    }

    private fun DetailedItem.anchoredProgress(progress: MediaProgressResponse): MediaProgress {
      val position =
        ChapterOrdering.position(this, ChapterLocation(chapterId = progress.episodeId ?: "", offset = progress.currentTime))
          // the episode is gone from the item: past the end, so that the progress is trimmed
          ?: (chapters.lastOrNull()?.end ?: 0.0) + progress.currentTime

      return MediaProgress(
        currentTime = position,
        isFinished = progress.isFinished,
        lastUpdate = progress.lastUpdate,
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
