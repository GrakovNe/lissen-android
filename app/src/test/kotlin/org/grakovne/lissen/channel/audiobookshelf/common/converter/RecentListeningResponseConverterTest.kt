package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedItemMediaResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedItemMetadataResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecentListeningResponseConverterTest {
  private val converter = RecentListeningResponseConverter()

  private val continueLabel = "LabelContinueListening"

  private fun feedItem(
    id: String,
    title: String = "Title",
    author: String? = null,
    podcastAuthor: String? = null,
    subtitle: String? = null,
    hasMedia: Boolean = true,
  ) = PersonalizedFeedItemResponse(
    id = id,
    libraryId = "lib-1",
    media =
      if (hasMedia) {
        PersonalizedFeedItemMediaResponse(
          id = "media-$id",
          metadata =
            PersonalizedFeedItemMetadataResponse(
              title = title,
              subtitle = subtitle,
              authorName = author,
              author = podcastAuthor,
            ),
        )
      } else {
        null
      },
  )

  private fun feed(
    label: String,
    items: List<PersonalizedFeedItemResponse>,
  ) = PersonalizedFeedResponse(id = "feed-1", labelStringKey = label, entities = items)

  @Test
  fun `empty feed list returns empty result`() {
    assertTrue(converter.apply(emptyList(), emptyMap()).isEmpty())
  }

  @Test
  fun `only LabelContinueListening section is processed`() {
    val response =
      listOf(
        feed("LabelRecentlyAdded", listOf(feedItem("1"))),
        feed(continueLabel, listOf(feedItem("2"))),
      )
    val result = converter.apply(response, emptyMap())
    assertEquals(1, result.size)
    assertEquals("2", result[0].id)
  }

  @Test
  fun `missing LabelContinueListening returns empty result`() {
    val response = listOf(feed("LabelSomethingElse", listOf(feedItem("1"))))
    assertTrue(converter.apply(response, emptyMap()).isEmpty())
  }

  @Test
  fun `duplicate ids are deduplicated`() {
    val response =
      listOf(
        feed(continueLabel, listOf(feedItem("same-id"), feedItem("same-id"))),
      )
    val result = converter.apply(response, emptyMap())
    assertEquals(1, result.size)
  }

  @Test
  fun `items with null media are filtered out`() {
    val response =
      listOf(
        feed(continueLabel, listOf(feedItem("1", hasMedia = true), feedItem("2", hasMedia = false))),
      )
    val result = converter.apply(response, emptyMap())
    assertEquals(1, result.size)
    assertEquals("1", result[0].id)
  }

  @Test
  fun `progress percentage is calculated from fractional value`() {
    val response = listOf(feed(continueLabel, listOf(feedItem("1"))))
    val progress = mapOf("1" to (1000L to 0.75))
    val result = converter.apply(response, progress)
    assertEquals(75, result[0].listenedPercentage)
  }

  @Test
  fun `progress last update is set from map`() {
    val response = listOf(feed(continueLabel, listOf(feedItem("1"))))
    val progress = mapOf("1" to (9999L to 0.5))
    val result = converter.apply(response, progress)
    assertEquals(9999L, result[0].listenedLastUpdate)
  }

  @Test
  fun `missing progress gives null percentage and null update`() {
    val response = listOf(feed(continueLabel, listOf(feedItem("1"))))
    val result = converter.apply(response, emptyMap())
    assertNull(result[0].listenedPercentage)
    assertNull(result[0].listenedLastUpdate)
  }

  @Test
  fun `podcast author falls back to the author field when authorName is absent`() {
    val response =
      listOf(
        feed(continueLabel, listOf(feedItem("1", author = null, podcastAuthor = "Podcast Host"))),
      )

    val book = converter.apply(response, emptyMap()).single()

    assertEquals("Podcast Host", book.author)
  }

  @Test
  fun `authorName wins over the podcast author field`() {
    val response =
      listOf(
        feed(continueLabel, listOf(feedItem("1", author = "Book Author", podcastAuthor = "Podcast Host"))),
      )

    val book = converter.apply(response, emptyMap()).single()

    assertEquals("Book Author", book.author)
  }

  @Test
  fun `book fields are mapped from media metadata`() {
    val response =
      listOf(
        feed(continueLabel, listOf(feedItem("1", title = "My Book", author = "Author A", subtitle = "Sub"))),
      )
    val book = converter.apply(response, emptyMap()).single()
    assertEquals("My Book", book.title)
    assertEquals("Author A", book.author)
    assertEquals("Sub", book.subtitle)
  }
}
