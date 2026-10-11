package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.LibraryPlaylistsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistMetadataResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playlist.PlaylistResponse
import org.grakovne.lissen.common.mergeAuthorNames
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookPlaylist
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaylistItem
import org.grakovne.lissen.domain.page
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryPlaylistsResponseConverter
  @Inject
  constructor() {
    // abs returns every playlist of the library with its items in one response, so the list is paged here
    fun apply(
      response: LibraryPlaylistsResponse,
      pageSize: Int,
      pageNumber: Int,
    ): PagedItems<LibraryEntry> =
      response
        .results
        .map { it.toEntry() }
        .sortedBy { it.name.lowercase() }
        .page(pageSize, pageNumber)

    fun playlistsOf(
      response: LibraryPlaylistsResponse,
      itemId: String,
    ): List<BookPlaylist> =
      response
        .results
        .flatMap { playlist ->
          playlist
            .items
            .withIndex()
            .filter { (_, item) -> item.libraryItemId == itemId }
            .map { (position, item) ->
              BookPlaylist(
                id = playlist.id,
                name = playlist.name,
                position = position,
                episodeId = item.episodeId,
              )
            }
        }

    private fun PlaylistResponse.toEntry() =
      LibraryEntry.PlaylistEntry(
        id = id,
        name = name,
        items = items.mapNotNull { it.toPlaylistItem() },
      )

    private fun PlaylistItemResponse.toPlaylistItem(): PlaylistItem? {
      val metadata = libraryItem?.media?.metadata ?: return null
      val title = metadata.title ?: return null

      val book =
        Book(
          id = libraryItemId,
          title = title,
          subtitle = metadata.subtitle,
          author = metadata.authorLine(),
          series = metadata.seriesLine(),
        )

      return when (episodeId) {
        null -> PlaylistItem.BookItem(book)
        else -> episode?.let { PlaylistItem.EpisodeItem(book = book, episodeId = episodeId, title = it.title) }
      }
    }

    private fun PlaylistMetadataResponse.authorLine(): String? =
      mergeAuthorNames(listOfNotNull(authorName) + authors.orEmpty().map { it.name } + listOfNotNull(author))

    private fun PlaylistMetadataResponse.seriesLine(): String? =
      seriesName
        ?: series
          ?.takeIf { it.isNotEmpty() }
          ?.joinToString(", ") { entry ->
            buildString {
              append(entry.name)
              entry.sequence
                ?.takeIf(String::isNotBlank)
                ?.let { append(" #$it") }
            }
          }
  }
