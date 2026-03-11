package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItem
import org.grakovne.lissen.lib.domain.PlayingItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastSearchItemsConverter
  @Inject
  constructor() {
    fun apply(response: List<PodcastItem>): List<PlayingItem> {
      return response
        .mapNotNull {
          val title = it.media.metadata.title ?: return@mapNotNull null

          PlayingItem(
            id = it.id,
            title = title,
            subtitle = null,
            series = null,
            author = it.media.metadata.author,
          )
        }
    }
  }
