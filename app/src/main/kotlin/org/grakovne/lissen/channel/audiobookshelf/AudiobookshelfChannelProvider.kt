package org.grakovne.lissen.channel.audiobookshelf

import org.grakovne.lissen.channel.audiobookshelf.common.api.AudiobookshelfAuthService
import org.grakovne.lissen.channel.audiobookshelf.library.LibraryAudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.podcast.PodcastAudiobookshelfChannel
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.ChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfChannelProvider
  @Inject
  constructor(
    private val podcastAudiobookshelfChannel: PodcastAudiobookshelfChannel,
    private val libraryAudiobookshelfChannel: LibraryAudiobookshelfChannel,
    private val audiobookshelfAuthService: AudiobookshelfAuthService,
    private val sharedPreferences: LibraryPreferences,
  ) : ChannelProvider {
    override fun provideMediaChannel(): MediaChannel = provideMediaChannel(activeLibraryType())

    fun provideMediaChannel(libraryType: LibraryType?): MediaChannel =
      when (resolveLibraryType(libraryType)) {
        LibraryType.LIBRARY -> libraryAudiobookshelfChannel
        LibraryType.PODCAST -> podcastAudiobookshelfChannel
      }

    override fun provideChannelAuth(): ChannelAuthService = audiobookshelfAuthService

    /** An item that does not know its library type is taken to belong to the active library. */
    fun resolveLibraryType(libraryType: LibraryType?): LibraryType = libraryType ?: activeLibraryType()

    private fun activeLibraryType(): LibraryType = sharedPreferences.getPreferredLibraryType()
  }
