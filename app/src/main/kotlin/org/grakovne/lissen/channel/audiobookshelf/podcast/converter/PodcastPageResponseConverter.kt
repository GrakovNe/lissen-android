package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlayingItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastPageResponseConverter
  @Inject
  constructor() {
    fun apply(response: PodcastItemsResponse): PagedItems<PlayingItem> =
      response
        .results
        .mapNotNull {
          val title = it.media.metadata.title ?: return@mapNotNull null

          PlayingItem(
            id = it.id,
            title = title,
            subtitle = null,
            series = null,
            author = it.media.metadata.author,
          )
        }.let {
          PagedItems(
            items = it,
            currentPage = response.page,
            totalItems = response.total,
          )
        }
  }
