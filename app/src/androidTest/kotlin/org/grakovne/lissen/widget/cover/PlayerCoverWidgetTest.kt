package org.grakovne.lissen.widget.cover

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.test.core.app.ApplicationProvider
import org.junit.Test

class PlayerCoverWidgetTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun showsPauseIconWhilePlaying() =
    runGlanceAppWidgetUnitTest {
      setAppWidgetSize(DpSize(160.dp, 160.dp))
      setState(
        preferencesOf(
          PlayerCoverWidget.bookId to "book-1",
          PlayerCoverWidget.isPlaying to true,
        ),
      )
      provideComposable { PlayerCoverWidget().Content(context) }

      onNode(hasTestTag(PlayerCoverWidget.pauseTestTag)).assertExists()
      onNode(hasTestTag(PlayerCoverWidget.playTestTag)).assertDoesNotExist()
    }

  @Test
  fun showsPlayIconWhilePaused() =
    runGlanceAppWidgetUnitTest {
      setAppWidgetSize(DpSize(80.dp, 80.dp))
      setState(
        preferencesOf(
          PlayerCoverWidget.bookId to "book-1",
          PlayerCoverWidget.isPlaying to false,
        ),
      )
      provideComposable { PlayerCoverWidget().Content(context) }

      onNode(hasTestTag(PlayerCoverWidget.playTestTag)).assertExists()
      onNode(hasTestTag(PlayerCoverWidget.pauseTestTag)).assertDoesNotExist()
    }
}
