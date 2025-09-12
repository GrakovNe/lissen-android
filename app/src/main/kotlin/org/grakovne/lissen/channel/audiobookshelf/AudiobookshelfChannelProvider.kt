package org.grakovne.lissen.channel.audiobookshelf

import org.grakovne.lissen.channel.audiobookshelf.common.UnknownAudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudiobookshelfAuthService
import org.grakovne.lissen.channel.audiobookshelf.library.LibraryAudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.podcast.PodcastAudiobookshelfChannel
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.ChannelCode
import org.grakovne.lissen.channel.common.ChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfChannelProvider
  @Inject
  constructor(
    private val podcastAudiobookshelfChannel: PodcastAudiobookshelfChannel,
    private val libraryAudiobookshelfChannel: LibraryAudiobookshelfChannel,
    private val unknownAudiobookshelfChannel: UnknownAudiobookshelfChannel,
    private val audiobookshelfAuthService: AudiobookshelfAuthService,
    private val sharedPreferences: LissenSharedPreferences,
  ) : ChannelProvider {
    override fun provideMediaChannel(): MediaChannel {
      val libraryType =
        sharedPreferences
          .getPreferredLibrary()
          ?.type
          ?: LibraryType.UNKNOWN

      return when (libraryType) {
        LibraryType.LIBRARY -> libraryAudiobookshelfChannel
        LibraryType.PODCAST -> podcastAudiobookshelfChannel
        LibraryType.UNKNOWN -> unknownAudiobookshelfChannel
      }
    }

    override fun provideChannelAuth(): ChannelAuthService = audiobookshelfAuthService

    override fun getChannelCode(): ChannelCode = ChannelCode.AUDIOBOOKSHELF
  }
