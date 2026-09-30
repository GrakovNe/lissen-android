package org.grakovne.lissen.ui.screens.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRefreshTest {
  @Test
  fun `refresh starts paging libraries and recent listening without serial waits`() =
    runTest {
      val librariesFinished = CompletableDeferred<Unit>()
      val recentFinished = CompletableDeferred<Unit>()
      val started = mutableSetOf<String>()

      val refresh =
        async {
          refreshLibraryContent(
            refreshPaging = { started += "paging" },
            fetchLibraries = {
              started += "libraries"
              librariesFinished.await()
            },
            fetchRecentListening = {
              started += "recent"
              recentFinished.await()
            },
          )
        }

      runCurrent()

      assertEquals(setOf("paging", "libraries", "recent"), started)
      assertFalse(refresh.isCompleted)

      librariesFinished.complete(Unit)
      runCurrent()
      assertFalse(refresh.isCompleted)

      recentFinished.complete(Unit)
      refresh.await()
    }
}
