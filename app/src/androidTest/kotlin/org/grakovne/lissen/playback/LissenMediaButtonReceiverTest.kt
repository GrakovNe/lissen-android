package org.grakovne.lissen.playback

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LissenMediaButtonReceiverTest {
  private lateinit var context: Context
  private lateinit var preferences: PlaybackPreferences
  private lateinit var receiver: TestMediaButtonReceiver

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    preferences = mockk(relaxed = true)
    receiver = TestMediaButtonReceiver(preferences)
  }

  @Test
  fun shouldStartForegroundService_noStoredPlayingItem_returnsFalse() {
    every { preferences.getPlayingItem() } returns null

    val result = receiver.exposeShouldStartForegroundService(context, Intent())

    assertFalse(result)
  }

  @Test
  fun shouldStartForegroundService_storedPlayingItemWithChapters_returnsTrue() {
    every { preferences.getPlayingItem() } returns makePlayingItem(chapterCount = 1, fileCount = 1)

    val result = receiver.exposeShouldStartForegroundService(context, Intent())

    assertTrue(result)
  }

  @Test
  fun shouldStartForegroundService_storedPlayingItemWithoutChapters_returnsFalse() {
    every { preferences.getPlayingItem() } returns makePlayingItem(chapterCount = 0, fileCount = 1)

    val result = receiver.exposeShouldStartForegroundService(context, Intent())

    assertFalse(result)
  }

  @Test
  fun shouldStartForegroundService_storedPlayingItemWithChaptersButWithoutFiles_returnsFalse() {
    every { preferences.getPlayingItem() } returns makePlayingItem(chapterCount = 1, fileCount = 0)

    val result = receiver.exposeShouldStartForegroundService(context, Intent())

    assertFalse(result)
  }

  private fun makePlayingItem(
    chapterCount: Int,
    fileCount: Int,
  ) = DetailedItem(
    id = "book-1",
    title = "My Book",
    subtitle = null,
    author = "Author",
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files =
      (0 until fileCount).map { index ->
        BookFile(
          id = "f-$index",
          name = "track-${index + 1}.mp3",
          duration = 100.0,
          size = null,
          mimeType = "audio/mpeg",
        )
      },
    chapters =
      (0 until chapterCount).map { index ->
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 150.0,
          start = 0.0,
          end = 150.0,
          title = "Chapter ${index + 1}",
          id = "c-$index",
        )
      },
    progress = null,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private class TestMediaButtonReceiver(
    mockPreferences: PlaybackPreferences,
  ) : LissenMediaButtonReceiver() {
    init {
      preferences = mockPreferences
    }

    fun exposeShouldStartForegroundService(
      context: Context,
      intent: Intent,
    ) = shouldStartForegroundService(context, intent)
  }
}
