package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.LibraryPlaylistsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistAuthorResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistEpisodeResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistLibraryItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistMediaResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistMetadataResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistSeriesResponse
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookPlaylist
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PlaylistItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryPlaylistsResponseConverterTest {
  private val converter = LibraryPlaylistsResponseConverter()

  private fun metadata(
    title: String?,
    author: String? = null,
    authorName: String? = null,
    authors: List<String>? = null,
    seriesName: String? = null,
    series: List<Pair<String, String?>>? = null,
  ) = PlaylistMetadataResponse(
    title = title,
    subtitle = null,
    author = author,
    authorName = authorName,
    authors = authors?.map { PlaylistAuthorResponse(it) },
    seriesName = seriesName,
    series = series?.map { (name, sequence) -> PlaylistSeriesResponse(name, sequence) },
  )

  private fun item(
    itemId: String,
    metadata: PlaylistMetadataResponse?,
    episodeId: String? = null,
    episodeTitle: String? = null,
  ) = PlaylistItemResponse(
    libraryItemId = itemId,
    episodeId = episodeId,
    libraryItem = metadata?.let { PlaylistLibraryItemResponse(id = itemId, media = PlaylistMediaResponse(it)) },
    episode = episodeTitle?.let { PlaylistEpisodeResponse(id = episodeId ?: "", title = it) },
  )

  private val books =
    PlaylistResponse(
      id = "pl-books",
      name = "Winter reads",
      items =
        listOf(
          item("b2", metadata("Dune Messiah", authors = listOf("Frank Herbert"), series = listOf("Dune" to "2"))),
          item("b1", metadata("Dune", authorName = "Frank Herbert", seriesName = "Dune #1")),
          item("gone", null),
        ),
    )

  private val episodes =
    PlaylistResponse(
      id = "pl-episodes",
      name = "Commute",
      items =
        listOf(
          item("p1", metadata("Daily News", author = "Newsroom"), episodeId = "e1", episodeTitle = "Monday"),
          item("p2", metadata("Tech Talk", author = "Hosts"), episodeId = "e7", episodeTitle = "Episode 7"),
          item("p1", metadata("Daily News", author = "Newsroom"), episodeId = "e2", episodeTitle = "Tuesday"),
        ),
    )

  private val response = LibraryPlaylistsResponse(results = listOf(books, episodes))

  @Test
  fun `lists playlists by name, counting and covering only the rows that will show`() {
    val page = converter.apply(response, pageSize = 20, pageNumber = 0)

    assertEquals(
      listOf(
        LibraryEntry.PlaylistEntry(id = "pl-episodes", name = "Commute", itemCount = 3, coverItemIds = listOf("p1", "p2")),
        LibraryEntry.PlaylistEntry(id = "pl-books", name = "Winter reads", itemCount = 2, coverItemIds = listOf("b2", "b1")),
      ),
      page.items,
    )
  }

  @Test
  fun `pages the whole list and reports its full size`() {
    val page = converter.apply(response, pageSize = 1, pageNumber = 1)

    assertEquals(listOf("pl-books"), page.items.map { (it as LibraryEntry.PlaylistEntry).id })
    assertEquals(1, page.currentPage)
    assertEquals(2, page.totalItems)
  }

  @Test
  fun `keeps the playlist order of books and reads author and series from either item form`() {
    assertEquals(
      listOf(
        PlaylistItem(Book(id = "b2", title = "Dune Messiah", subtitle = null, author = "Frank Herbert", series = "Dune #2")),
        PlaylistItem(Book(id = "b1", title = "Dune", subtitle = null, author = "Frank Herbert", series = "Dune #1")),
      ),
      converter.items(response, "pl-books"),
    )
  }

  @Test
  fun `an episode row names the episode and carries the podcast as its book`() {
    val podcast = Book(id = "p1", title = "Daily News", subtitle = null, author = "Newsroom", series = null)

    assertEquals(
      PlaylistItem(book = podcast, episodeId = "e1", episodeTitle = "Monday"),
      converter.items(response, "pl-episodes").first(),
    )
  }

  @Test
  fun `an unknown playlist has no items`() {
    assertEquals(emptyList<PlaylistItem>(), converter.items(response, "missing"))
  }

  @Test
  fun `memberships list every place an item or its episodes hold`() {
    assertEquals(
      listOf(
        BookPlaylist(id = "pl-episodes", name = "Commute", position = 0, episodeId = "e1"),
        BookPlaylist(id = "pl-episodes", name = "Commute", position = 2, episodeId = "e2"),
      ),
      converter.memberships(response, "p1"),
    )

    assertEquals(
      listOf(BookPlaylist(id = "pl-books", name = "Winter reads", position = 1, episodeId = null)),
      converter.memberships(response, "b1"),
    )

    assertEquals(emptyList<BookPlaylist>(), converter.memberships(response, "elsewhere"))
  }
}
