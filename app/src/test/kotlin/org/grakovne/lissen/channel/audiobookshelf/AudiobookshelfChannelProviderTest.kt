package org.grakovne.lissen.channel.audiobookshelf

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudiobookshelfAuthService
import org.grakovne.lissen.channel.audiobookshelf.library.LibraryAudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.podcast.PodcastAudiobookshelfChannel
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudiobookshelfChannelProviderTest {
  private val podcastChannel = mockk<PodcastAudiobookshelfChannel>()
  private val libraryChannel = mockk<LibraryAudiobookshelfChannel>()
  private val authService = mockk<AudiobookshelfAuthService>()
  private val preferences = mockk<LibraryPreferences>()

  private val provider = AudiobookshelfChannelProvider(podcastChannel, libraryChannel, authService, preferences)

  @Test
  fun `provideMediaChannel falls back to the active library type when the item type is missing`() {
    every { preferences.getPreferredLibraryType() } returns LibraryType.PODCAST

    assertEquals(podcastChannel, provider.provideMediaChannel(null))
  }

  @Test
  fun `provideMediaChannel prefers the item type over the active library type`() {
    every { preferences.getPreferredLibraryType() } returns LibraryType.PODCAST

    assertEquals(libraryChannel, provider.provideMediaChannel(LibraryType.LIBRARY))
  }
}
