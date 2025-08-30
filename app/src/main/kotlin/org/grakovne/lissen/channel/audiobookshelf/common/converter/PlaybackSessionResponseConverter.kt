package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.domain.PlaybackSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionResponseConverter
  @Inject
  constructor() {
    fun apply(response: PlaybackSessionResponse): PlaybackSession =
      PlaybackSession(
        sessionId = response.id,
        itemId = response.libraryItemId,
      )
  }
