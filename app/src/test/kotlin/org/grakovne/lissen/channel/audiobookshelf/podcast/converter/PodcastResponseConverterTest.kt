package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.AudioFileMetadata
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastAudioFileResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastEpisodeResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastMedia
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastMediaMetadataResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.domain.BookChapterState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PodcastResponseConverterTest {
  private val converter = PodcastResponseConverter()

  private fun audioFile(
    ino: String,
    duration: Double?,
  ) = PodcastAudioFileResponse(
    ino = ino,
    duration = duration,
    mimeType = "audio/mpeg",
    metadata = AudioFileMetadata(filename = "$ino.mp3", ext = "mp3", size = 1024L),
  )

  private fun episode(
    id: String,
    season: String? = null,
    episode: String? = null,
    pubDate: String? = null,
    title: String = "Episode $id",
    duration: Double? = 100.0,
  ) = PodcastEpisodeResponse(
    id = id,
    season = season,
    episode = episode,
    pubDate = pubDate,
    title = title,
    audioFile = audioFile(id, duration),
  )

  private fun podcast(episodes: List<PodcastEpisodeResponse>?) =
    PodcastResponse(
      id = "podcast-1",
      ino = "ino-1",
      libraryId = "lib-1",
      media =
        PodcastMedia(
          metadata =
            PodcastMediaMetadataResponse(
              title = "My Podcast",
              author = "Some Author",
              description = "Description",
              publisher = "Publisher",
            ),
          episodes = episodes,
        ),
      addedAt = 111L,
      ctimeMs = 222L,
    )

  @Test
  fun `maps basic podcast fields`() {
    val result = converter.apply(podcast(emptyList()))

    assertEquals("podcast-1", result.id)
    assertEquals("My Podcast", result.title)
    assertEquals("lib-1", result.libraryId)
    assertEquals("Some Author", result.author)
    assertNull(result.narrator)
    assertEquals(false, result.localProvided)
    assertEquals("Description", result.abstract)
    assertEquals("Publisher", result.publisher)
    assertEquals(emptyList<Any>(), result.series)
    assertEquals(111L, result.createdAt)
    assertEquals(222L, result.updatedAt)
  }

  @Test
  fun `orders episodes by pubDate then season then episode number`() {
    val episodes =
      listOf(
        episode(id = "e1", pubDate = "Wed, 02 Jan 2024 00:00:00 +0000"),
        episode(id = "e2", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000"),
        episode(id = "e3", pubDate = null, season = "1", episode = "2"),
        episode(id = "e4", pubDate = null, season = "1", episode = "1"),
      )

    val result = converter.apply(podcast(episodes))

    assertEquals(listOf("e4", "e3", "e2", "e1"), result.files.map { it.id })
  }

  @Test
  fun `sorts episode with unparseable pubDate ahead of dated episodes`() {
    val episodes =
      listOf(
        episode(id = "bad", pubDate = "not-a-date"),
        episode(id = "good", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000"),
      )

    val result = converter.apply(podcast(episodes))

    assertEquals(listOf("bad", "good"), result.files.map { it.id })
  }

  @Test
  fun `builds chapters with accumulated start and end offsets`() {
    val episodes =
      listOf(
        episode(id = "e1", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", duration = 100.0),
        episode(id = "e2", pubDate = "Tue, 02 Jan 2024 00:00:00 +0000", duration = 50.0),
      )

    val result = converter.apply(podcast(episodes))

    assertEquals(2, result.chapters.size)
    assertEquals(0.0, result.chapters[0].start)
    assertEquals(100.0, result.chapters[0].end)
    assertEquals(100.0, result.chapters[1].start)
    assertEquals(150.0, result.chapters[1].end)
  }

  @Test
  fun `treats null episode duration as zero when building files and chapters`() {
    val episodes = listOf(episode(id = "e1", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", duration = null))

    val result = converter.apply(podcast(episodes))

    assertEquals(0.0, result.files[0].duration)
    assertEquals(0.0, result.chapters[0].duration)
  }

  @Test
  fun `marks chapter finished when matching progress isFinished is true`() {
    val episodes = listOf(episode(id = "e1", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", duration = 100.0))
    val progress =
      listOf(
        MediaProgressResponse(
          libraryItemId = "podcast-1",
          episodeId = "e1",
          currentTime = 10.0,
          isFinished = true,
          lastUpdate = 999L,
          progress = 0.1,
        ),
      )

    val result = converter.apply(podcast(episodes), progress)

    assertEquals(BookChapterState.FINISHED, result.chapters[0].podcastEpisodeState)
  }

  @Test
  fun `marks chapter finished when progress ratio exceeds threshold even if isFinished is false`() {
    val episodes = listOf(episode(id = "e1", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", duration = 100.0))
    val progress =
      listOf(
        MediaProgressResponse(
          libraryItemId = "podcast-1",
          episodeId = "e1",
          currentTime = 95.0,
          isFinished = false,
          lastUpdate = 999L,
          progress = 0.95,
        ),
      )

    val result = converter.apply(podcast(episodes), progress)

    assertEquals(BookChapterState.FINISHED, result.chapters[0].podcastEpisodeState)
  }

  @Test
  fun `leaves chapter state null when no progress exists for episode`() {
    val episodes = listOf(episode(id = "e1", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", duration = 100.0))

    val result = converter.apply(podcast(episodes), emptyList())

    assertNull(result.chapters[0].podcastEpisodeState)
  }

  @Test
  fun `computes total current time from latest progress plus durations of preceding episodes`() {
    val episodes =
      listOf(
        episode(id = "e1", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", duration = 100.0),
        episode(id = "e2", pubDate = "Tue, 02 Jan 2024 00:00:00 +0000", duration = 50.0),
      )
    val progress =
      listOf(
        MediaProgressResponse(
          libraryItemId = "podcast-1",
          episodeId = "e2",
          currentTime = 10.0,
          isFinished = false,
          lastUpdate = 999L,
          progress = 0.2,
        ),
      )

    val result = converter.apply(podcast(episodes), progress)

    assertEquals(110.0, result.progress?.currentTime)
    assertEquals(999L, result.progress?.lastUpdate)
  }

  @Test
  fun `returns null progress when no progress responses provided`() {
    val result = converter.apply(podcast(emptyList()), emptyList())

    assertNull(result.progress)
  }

  @Test
  fun `handles null episodes list by producing empty files and chapters`() {
    val result = converter.apply(podcast(null))

    assertEquals(emptyList<Any>(), result.files)
    assertEquals(emptyList<Any>(), result.chapters)
  }
}
