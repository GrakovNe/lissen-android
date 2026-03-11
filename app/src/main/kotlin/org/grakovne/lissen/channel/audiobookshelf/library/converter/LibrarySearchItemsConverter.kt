package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.lib.domain.PlayingItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySearchItemsConverter
  @Inject
  constructor() {
    fun apply(response: List<LibraryItem>) =
      response
        .mapNotNull {
          val title = it.media.metadata.title ?: return@mapNotNull null

          PlayingItem(
            id = it.id,
            title = title,
            series = it.media.metadata.seriesName,
            subtitle = it.media.metadata.subtitle,
            author = it.media.metadata.authorName,
          )
        }
  }
