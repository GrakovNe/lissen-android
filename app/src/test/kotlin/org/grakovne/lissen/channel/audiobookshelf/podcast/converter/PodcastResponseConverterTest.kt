package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.AudioFileMetadata
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastAudioFileResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastEpisodeResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastMedia
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastMediaMetadataResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.LibraryType
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
    assertEquals(LibraryType.PODCAST, result.libraryType)
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
  fun `hands the item over in the canonical order with the canonical index`() {
    val episodes =
      listOf(
        episode(id = "e1", pubDate = "Tue, 02 Jan 2024 00:00:00 +0000"),
        episode(id = "e2", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000"),
        episode(id = "e3", pubDate = null, season = "1", episode = "2"),
      )

    val result = converter.apply(podcast(episodes))

    // undated first, then by date; files follow the chapters
    assertEquals(listOf("e3", "e2", "e1"), result.chapters.map { it.id })
    assertEquals(listOf("e3", "e2", "e1"), result.files.map { it.id })
    assertEquals(listOf(0, 1, 2), result.chapters.map { it.index })
    assertEquals(listOf(0.0, 100.0, 200.0), result.chapters.map { it.start })
  }

  @Test
  fun `a finished episode resumes at the start of its canonical successor whatever the server order`() {
    // server order B, A, C; canonical (by date) A, B, C
    val episodes =
      listOf(
        episode(id = "B", pubDate = "Tue, 02 Jan 2024 00:00:00 +0000", duration = 200.0),
        episode(id = "A", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", duration = 100.0),
        episode(id = "C", pubDate = "Wed, 03 Jan 2024 00:00:00 +0000", duration = 300.0),
      )
    // the server reports B as finished: currentTime equals its duration
    val progress =
      listOf(
        MediaProgressResponse(
          libraryItemId = "podcast-1",
          episodeId = "B",
          currentTime = 200.0,
          isFinished = true,
          lastUpdate = 999L,
          progress = 1.0,
        ),
      )

    val result = converter.apply(podcast(episodes), progress)

    assertEquals(listOf("A", "B", "C"), result.chapters.map { it.id })
    // end of B in the canonical timeline, which is where C starts
    assertEquals(300.0, result.progress?.currentTime)
  }

  @Test
  fun `progress on an episode the item no longer has lands past the end`() {
    val episodes = listOf(episode(id = "e1", duration = 100.0))
    val progress =
      listOf(
        MediaProgressResponse(
          libraryItemId = "podcast-1",
          episodeId = "gone",
          currentTime = 10.0,
          isFinished = false,
          lastUpdate = 999L,
          progress = 0.2,
        ),
      )

    val result = converter.apply(podcast(episodes), progress)

    assertEquals(110.0, result.progress?.currentTime)
  }

  @Test
  fun `exposes ordering keys on chapters`() {
    val result =
      converter.apply(podcast(listOf(episode(id = "e1", pubDate = "Mon, 01 Jan 2024 00:00:00 +0000", season = "3", episode = "7"))))

    val chapter = result.chapters.single()
    assertEquals(1_704_067_200_000L, chapter.publishedAt)
    assertEquals("3", chapter.season)
    assertEquals("7", chapter.episode)
    assertEquals("e1.mp3", chapter.fileName)
  }

  @Test
  fun `published date is parsed from pubDate with the pattern every version used`() {
    val result =
      converter.apply(
        podcast(
          listOf(
            episode(id = "e1", pubDate = "Mon, 01 Jan 2024 12:30:00 +0200"),
            episode(id = "e2", pubDate = "01 Jan 2024"),
            episode(id = "e3", pubDate = null),
          ),
        ),
      )

    assertEquals(1_704_105_000_000L, result.chapters.first { it.id == "e1" }.publishedAt)
    assertNull(result.chapters.first { it.id == "e2" }.publishedAt)
    assertNull(result.chapters.first { it.id == "e3" }.publishedAt)
  }

  @Test
  fun `pubDate is read from the episode payload`() {
    val json =
      """
      {"id":"e1","season":"1","episode":"2","pubDate":"Mon, 01 Jan 2024 00:00:00 +0000","publishedAt":1704067200000,
       "title":"One","audioFile":{"ino":"f1","duration":10.0,"mimeType":"audio/mpeg","metadata":{"filename":"f1.mp3","ext":"mp3","size":1}}}
      """.trimIndent()

    val episode = moshi.adapter(PodcastEpisodeResponse::class.java).fromJson(json)!!

    assertEquals("Mon, 01 Jan 2024 00:00:00 +0000", episode.pubDate)
    assertEquals(
      1_704_067_200_000L,
      converter
        .apply(podcast(listOf(episode)))
        .chapters
        .single()
        .publishedAt,
    )
  }

  @Test
  fun `builds chapters with accumulated start and end offsets`() {
    val episodes =
      listOf(
        episode(id = "e1", duration = 100.0),
        episode(id = "e2", duration = 50.0),
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
    val episodes = listOf(episode(id = "e1", duration = null))

    val result = converter.apply(podcast(episodes))

    assertEquals(0.0, result.files[0].duration)
    assertEquals(0.0, result.chapters[0].duration)
  }

  @Test
  fun `marks chapter finished when matching progress isFinished is true`() {
    val episodes = listOf(episode(id = "e1", duration = 100.0))
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
    val episodes = listOf(episode(id = "e1", duration = 100.0))
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
    val episodes = listOf(episode(id = "e1", duration = 100.0))

    val result = converter.apply(podcast(episodes), emptyList())

    assertNull(result.chapters[0].podcastEpisodeState)
  }

  @Test
  fun `progress is the canonical offset of the latest episode progress`() {
    val episodes =
      listOf(
        episode(id = "e1", duration = 100.0),
        episode(id = "e2", duration = 50.0),
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
