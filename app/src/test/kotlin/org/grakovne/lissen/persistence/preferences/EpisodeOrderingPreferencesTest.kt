package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeOrderingPreferencesTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val preferences = LibraryPreferences(SecurePreferenceStore(context))

  private val byTitleDesc = EpisodeOrderingConfiguration(EpisodeOrderingOption.TITLE, LibraryOrderingDirection.DESCENDING)
  private val byFileAsc = EpisodeOrderingConfiguration(EpisodeOrderingOption.FILE_NAME, LibraryOrderingDirection.ASCENDING)

  @Test
  fun `ordering is absent until the user picks one`() {
    assertNull(preferences.getEpisodeOrdering("podcast-1"))
  }

  @Test
  fun `ordering is stored per item`() {
    preferences.saveEpisodeOrdering("podcast-1", byTitleDesc)
    preferences.saveEpisodeOrdering("podcast-2", byFileAsc)

    assertEquals(byTitleDesc, preferences.getEpisodeOrdering("podcast-1"))
    assertEquals(byFileAsc, preferences.getEpisodeOrdering("podcast-2"))
    assertNull(preferences.getEpisodeOrdering("podcast-3"))
  }

  @Test
  fun `saving again replaces the previous choice for that item only`() {
    preferences.saveEpisodeOrdering("podcast-1", byTitleDesc)
    preferences.saveEpisodeOrdering("podcast-2", byFileAsc)
    preferences.saveEpisodeOrdering("podcast-1", byFileAsc)

    assertEquals(byFileAsc, preferences.getEpisodeOrdering("podcast-1"))
    assertEquals(byFileAsc, preferences.getEpisodeOrdering("podcast-2"))
  }

  @Test
  fun `flow emits the whole map on every change`() =
    runTest {
      val emissions = mutableListOf<Map<String, EpisodeOrderingConfiguration>>()
      val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          preferences.episodeOrderingFlow.collect { emissions += it }
        }

      preferences.saveEpisodeOrdering("podcast-1", byTitleDesc)

      assertEquals(listOf(emptyMap(), mapOf("podcast-1" to byTitleDesc)), emissions)
      collector.cancel()
    }

  @Test
  fun `an unreadable entry drops only itself`() {
    fakePreferences
      .edit()
      .putString(
        "episode_ordering",
        """{"podcast-1":{"option":"TITLE","direction":"DESCENDING"},"podcast-2":{"option":"NO_SUCH_OPTION","direction":"ASCENDING"}}""",
      ).commit()

    assertEquals(byTitleDesc, preferences.getEpisodeOrdering("podcast-1"))
    assertNull(preferences.getEpisodeOrdering("podcast-2"))

    preferences.saveEpisodeOrdering("podcast-3", byFileAsc)

    assertEquals(byTitleDesc, preferences.getEpisodeOrdering("podcast-1"))
    assertEquals(byFileAsc, preferences.getEpisodeOrdering("podcast-3"))
  }
}
