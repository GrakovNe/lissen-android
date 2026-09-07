package org.grakovne.lissen.widget.state

import android.content.Context
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.grakovne.lissen.R
import org.junit.Test

class PlayerStateWidgetTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val placeholder = context.getString(R.string.widget_placeholder_text)

  @Test
  fun showsPlaceholderWhenNothingIsPlaying() =
    runGlanceAppWidgetUnitTest {
      setState(preferencesOf())
      provideComposable { PlayerStateWidget().Content(context) }

      onNode(hasText(placeholder)).assertExists()
    }

  @Test
  fun showsChapterAndBookTitles() =
    runGlanceAppWidgetUnitTest {
      setState(
        preferencesOf(
          PlayerStateWidget.bookId to "book-1",
          PlayerStateWidget.title to "The Hobbit",
          PlayerStateWidget.chapterTitle to "An Unexpected Party",
        ),
      )
      provideComposable { PlayerStateWidget().Content(context) }

      onNode(hasText("The Hobbit")).assertExists()
      onNode(hasText("An Unexpected Party")).assertExists()
      onNode(hasText(placeholder)).assertDoesNotExist()
    }

  @Test
  fun hidesPlaceholderWhenBookHasNoChapterTitle() =
    runGlanceAppWidgetUnitTest {
      setState(
        preferencesOf(
          PlayerStateWidget.bookId to "book-1",
          PlayerStateWidget.title to "The Hobbit",
        ),
      )
      provideComposable { PlayerStateWidget().Content(context) }

      onNode(hasText(placeholder)).assertDoesNotExist()
    }
}
