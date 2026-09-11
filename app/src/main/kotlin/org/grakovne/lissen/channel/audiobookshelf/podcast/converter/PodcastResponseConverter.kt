package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastEpisodeResponse
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

@Singleton
class PodcastResponseConverter
  @Inject
  constructor() {
    fun apply(
      item: PodcastResponse,
      progressResponses: List<MediaProgressResponse> = emptyList(),
    ): DetailedItem {
      val orderedEpisodes =
        item
          .media
          .episodes
          ?.orderEpisode()

      val totalCurrentTime =
        progressResponses
          .maxByOrNull { it.lastUpdate }
          ?.let { progress ->
            orderedEpisodes
              ?.takeWhile { it.id != progress.episodeId }
              ?.sumOf { it.audioFile.duration ?: 0.0 }
              ?.plus(progress.currentTime)
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

      val filesAsChapters: List<PlayingChapter> =
        orderedEpisodes
          ?.fold(0.0 to mutableListOf<PlayingChapter>()) { (accDuration, chapters), episode ->
            chapters.add(
              PlayingChapter(
                start = accDuration,
                end = accDuration + (episode.audioFile.duration ?: 0.0),
                title = episode.title,
                duration = episode.audioFile.duration ?: 0.0,
                id = episode.id,
                available = true,
                podcastEpisodeState =
                  progressResponses
                    .find { it.episodeId == episode.id }
                    ?.let { hasFinished(it) },
              ),
            )
            accDuration + (episode.audioFile.duration ?: 0.0) to chapters
          }?.second
          ?: emptyList()

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
          orderedEpisodes
            ?.map {
              BookFile(
                id = it.audioFile.ino,
                name = it.title,
                duration = it.audioFile.duration ?: 0.0,
                mimeType = it.audioFile.mimeType,
                size = it.audioFile.metadata.size,
              )
            }
            ?: emptyList(),
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

    companion object {
      private const val FINISHED_PROGRESS_THRESHOLD = 0.9
      private const val PUB_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss Z"

      private data class EpisodeOrder(
        val publishedAt: Long?,
        val season: Int?,
        val episode: Int?,
      )

      // SimpleDateFormat is not thread-safe and podcast books are fetched concurrently,
      // so parsing happens once per sort with a dedicated instance instead of a shared one.
      private fun List<PodcastEpisodeResponse>.orderEpisode(): List<PodcastEpisodeResponse> {
        val dateFormat = SimpleDateFormat(PUB_DATE_PATTERN, Locale.ENGLISH)

        return map { item ->
          val publishedAt =
            try {
              item.pubDate?.let { dateFormat.parse(it)?.time }
            } catch (e: Exception) {
              Timber.w("Unable to parse episode pubDate '${item.pubDate}' due to: ${e.message}")
              null
            }
          EpisodeOrder(publishedAt, item.season.safeToInt(), item.episode.safeToInt()) to item
        }.sortedWith(
          compareBy({ it.first.publishedAt }, { it.first.season }, { it.first.episode }),
        ).map { it.second }
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
