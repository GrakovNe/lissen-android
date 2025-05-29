package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.PagedItems
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastPageResponseConverter
  @Inject
  constructor() {
    fun apply(response: PodcastItemsResponse): PagedItems<Book> =
      response
        .results
        .mapNotNull {
          val title = it.media.metadata.title ?: return@mapNotNull null

          Book(
            id = it.id,
            title = title,
            subtitle = null,
            series = null,
            author = it.media.metadata.author,
            duration = it.media.duration.toInt(),
            hasContent = it.media.numEpisodes?.let { count -> count > 0 } ?: true,
          )
        }.let {
          PagedItems(
            items = it,
            currentPage = response.page,
          )
        }
  }
