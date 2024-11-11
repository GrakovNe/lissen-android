package org.grakovne.lissen.channel.audiobookshelf.common.model.podcast

import org.grakovne.lissen.channel.audiobookshelf.common.model.library.LibrarySearchItemResponse

data class PodcastSearchResponse(
    val podcast: List<LibrarySearchItemResponse>
)
