package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItem
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemMedia
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastPageResponseConverterTest {
  private val converter = PodcastPageResponseConverter()

  private fun item(
    id: String,
    title: String?,
    author: String? = "Author",
  ) = PodcastItem(
    id = id,
    media = PodcastItemMedia(numEpisodes = 3, metadata = PodcastMetadata(title = title, author = author)),
  )

  @Test
  fun `maps podcast items to books carrying paging metadata`() {
    val response = PodcastItemsResponse(results = listOf(item("p1", "Title 1"), item("p2", "Title 2")), page = 2, total = 42)

    val result = converter.apply(response)

    assertEquals(listOf("p1", "p2"), result.items.map { it.id })
    assertEquals(listOf("Title 1", "Title 2"), result.items.map { it.title })
    assertEquals(2, result.currentPage)
    assertEquals(42, result.totalItems)
  }

  @Test
  fun `skips items with no title`() {
    val response = PodcastItemsResponse(results = listOf(item("p1", null), item("p2", "Title 2")), page = 0, total = 1)

    val result = converter.apply(response)

    assertEquals(listOf("p2"), result.items.map { it.id })
  }

  @Test
  fun `maps null author through to the book`() {
    val response = PodcastItemsResponse(results = listOf(item("p1", "Title 1", author = null)), page = 0, total = 1)

    val result = converter.apply(response)

    assertEquals(null, result.items[0].author)
  }
}
