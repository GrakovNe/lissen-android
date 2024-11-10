package org.grakovne.lissen

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.channel.audiobookshelf.library.LibraryAudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.podcast.PodcastAudiobookshelfChannel
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.channel.common.MediaChannel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaChannelModule {

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideMediaChannels(
        podcastAudiobookshelfChannel: PodcastAudiobookshelfChannel,
        libraryAudiobookshelfChannel: LibraryAudiobookshelfChannel
    ): Map<LibraryType, @JvmSuppressWildcards MediaChannel> {
        return mapOf(
            libraryAudiobookshelfChannel.getChannelCode() to libraryAudiobookshelfChannel,
            podcastAudiobookshelfChannel.getChannelCode() to podcastAudiobookshelfChannel
        )
    }
}
