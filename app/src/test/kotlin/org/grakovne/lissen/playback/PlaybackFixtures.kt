package org.grakovne.lissen.playback

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter

/**
 * Canonical order c0 (30s), c1 (40s), c2 (50s): total 120s. The descending configuration
 * puts c2 first, so a position inside c1 moves by 20s and a position inside c0 moves to
 * the very end of the item.
 */
object PlaybackFixtures {
  fun chapter(
    id: String,
    index: Int,
    duration: Double,
    publishedAt: Long,
    available: Boolean = true,
  ) = PlayingChapter(
    available = available,
    podcastEpisodeState = null,
    duration = duration,
    start = 0.0,
    end = duration,
    title = "Episode $id",
    id = id,
    index = index,
    publishedAt = publishedAt,
    season = null,
    episode = null,
    fileName = "$id.mp3",
  )

  fun podcast(
    id: String = "podcast",
    progress: MediaProgress? = null,
    chapters: List<PlayingChapter> =
      listOf(
        chapter(id = "c0", index = 0, duration = 30.0, publishedAt = 1L),
        chapter(id = "c1", index = 1, duration = 40.0, publishedAt = 2L),
        chapter(id = "c2", index = 2, duration = 50.0, publishedAt = 3L),
      ),
  ): DetailedItem {
    var accumulated = 0.0
    val bounded =
      chapters.map {
        val start = accumulated
        accumulated += it.duration
        it.copy(start = start, end = accumulated)
      }

    return DetailedItem(
      id = id,
      title = "Item",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = bounded.map { BookFile(id = "file-${it.id}", name = it.title, duration = it.duration, size = null, mimeType = "audio/mpeg") },
      chapters = bounded,
      progress = progress,
      libraryId = "lib",
      libraryType = LibraryType.PODCAST,
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
  }

  fun progress(at: Double) = MediaProgress(currentTime = at, isFinished = false, lastUpdate = 1L)

  fun descending() =
    EpisodeOrderingConfiguration(
      option = EpisodeOrderingOption.PUBLISHED_AT,
      direction = LibraryOrderingDirection.DESCENDING,
    )

  fun bookmark(
    position: Double,
    itemId: String = "podcast",
    createdAt: Long = 1L,
    title: String = "note",
  ) = Bookmark(
    libraryItemId = itemId,
    title = title,
    totalPosition = position,
    createdAt = createdAt,
    syncState = BookmarkSyncState.SYNCED,
  )
}
