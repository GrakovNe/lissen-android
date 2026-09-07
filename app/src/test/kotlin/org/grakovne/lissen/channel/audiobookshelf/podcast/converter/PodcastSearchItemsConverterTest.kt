package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItem
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemMedia
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastSearchItemsConverterTest {
  private val converter = PodcastSearchItemsConverter()

  private fun item(
    id: String,
    title: String?,
  ) = PodcastItem(id = id, media = PodcastItemMedia(numEpisodes = 1, metadata = PodcastMetadata(title = title, author = "Author")))

  @Test
  fun `maps podcast items to books`() {
    val result = converter.apply(listOf(item("p1", "Title 1"), item("p2", "Title 2")))

    assertEquals(listOf("p1", "p2"), result.map { it.id })
    assertEquals(listOf("Title 1", "Title 2"), result.map { it.title })
  }

  @Test
  fun `skips items missing a title`() {
    val result = converter.apply(listOf(item("p1", null), item("p2", "Title 2")))

    assertEquals(listOf("p2"), result.map { it.id })
  }

  @Test
  fun `returns empty list for empty input`() {
    val result = converter.apply(emptyList())

    assertEquals(emptyList<Any>(), result)
  }
}
