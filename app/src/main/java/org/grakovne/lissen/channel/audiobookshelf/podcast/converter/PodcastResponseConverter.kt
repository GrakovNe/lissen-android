package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastEpisodeResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.domain.BookChapter
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastResponseConverter @Inject constructor() {

    fun apply(
        item: PodcastResponse,
        progressResponse: MediaProgressResponse? = null,
    ): DetailedItem {
        val orderedEpisodes = item
            .media
            .episodes
            ?.orderEpisode()

        val filesAsChapters: List<BookChapter> =
            orderedEpisodes
                ?.fold(0.0 to mutableListOf<BookChapter>()) { (accDuration, chapters), file ->
                    chapters.add(
                        BookChapter(
                            start = accDuration,
                            end = accDuration + file.audioFile.duration,
                            title = file.title,
                            duration = file.audioFile.duration,
                            id = file.id,
                            available = true,
                        ),
                    )
                    accDuration + file.audioFile.duration to chapters
                }
                ?.second
                ?: emptyList()

        return DetailedItem(
            id = item.id,
            title = item.media.metadata.title,
            libraryId = item.libraryId,
            author = item.media.metadata.author,
            localProvided = false,
            files = orderedEpisodes
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
            progress = progressResponse
                ?.let {
                    MediaProgress(
                        currentTime = it.currentTime,
                        isFinished = it.isFinished,
                        lastUpdate = it.lastUpdate,
                    )
                },
        )
    }

    companion object {
        private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

        private fun List<PodcastEpisodeResponse>.orderEpisode() =
            this.sortedWith(
                compareBy<PodcastEpisodeResponse> { item ->
                    try {
                        item.pubDate?.let { dateFormat.parse(it)?.time }
                    } catch (e: Exception) {
                        null
                    }
                }
                    .thenBy { it.season.safeToInt() }
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
