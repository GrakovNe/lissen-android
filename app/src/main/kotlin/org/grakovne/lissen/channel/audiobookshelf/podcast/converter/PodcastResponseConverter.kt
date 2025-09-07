package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastEpisodeResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.lib.domain.BookChapterState
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.MediaProgress
import org.grakovne.lissen.lib.domain.PlayingChapter
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
              ?.sumOf { it.audioFile.duration }
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
                end = accDuration + episode.audioFile.duration,
                title = episode.title,
                duration = episode.audioFile.duration,
                id = episode.id,
                available = true,
                podcastEpisodeState =
                  progressResponses
                    .find { it.episodeId == episode.id }
                    ?.let { hasFinished(it) },
              ),
            )
            accDuration + episode.audioFile.duration to chapters
          }?.second
          ?: emptyList()

      return DetailedItem(
        id = item.id,
        title = item.media.metadata.title,
        subtitle = null,
        libraryId = item.libraryId,
        author = item.media.metadata.author,
        narrator = null,
        localProvided = false,
        files =
          orderedEpisodes
            ?.map {
              BookFile(
                id = it.audioFile.ino,
                name = it.title,
                duration = it.audioFile.duration,
                mimeType = it.audioFile.mimeType,
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
      private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

      private fun List<PodcastEpisodeResponse>.orderEpisode() =
        this.sortedWith(
          compareBy<PodcastEpisodeResponse> { item ->
            try {
              item.pubDate?.let { dateFormat.parse(it)?.time }
            } catch (e: Exception) {
              null
            }
          }.thenBy { it.season.safeToInt() }
            .thenBy { it.episode.safeToInt() },
        )

      private fun String?.safeToInt(): Int? {
        val maybeNumber = this?.takeIf { it.isNotBlank() }

        return try {
          maybeNumber?.toInt()
        } catch (ex: Exception) {
          null
        }
      }
    }
  }
