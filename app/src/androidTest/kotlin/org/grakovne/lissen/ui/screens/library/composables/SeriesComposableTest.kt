package org.grakovne.lissen.ui.screens.library.composables

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import io.mockk.mockk
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.junit.Rule
import org.junit.Test

class SeriesComposableTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val imageLoader = ImageLoader.Builder(context).build()
  private val navController = mockk<AppNavigationService>(relaxed = true)

  private fun series(id: String = "ser-1") =
    LibraryEntry.SeriesEntry(
      id = id,
      title = "Dune",
      author = "Frank Herbert",
      bookCount = 3,
      coverItemIds = listOf("b1", "b2", "b3"),
    )

  private fun book(
    id: String,
    sequence: String,
  ) = Book(id = id, subtitle = null, series = "Dune #$sequence", title = "Book $id", author = "Frank Herbert")

  @Test
  fun showsSeriesTitleAndTogglesOnClick() {
    var toggles = 0

    composeRule.setContent {
      SeriesComposable(
        series = series(),
        expanded = false,
        loading = false,
        books = emptyList(),
        imageLoader = imageLoader,
        navController = navController,
        onToggle = { toggles++ },
        onPrefetch = {},
      )
    }

    composeRule.onNodeWithText("Dune").assertIsDisplayed()
    composeRule.onNodeWithTag("seriesItem_ser-1").performClick()

    assert(toggles == 1) { "expected one toggle, got $toggles" }
  }

  @Test
  fun collapsedSeriesHidesBooks() {
    composeRule.setContent {
      SeriesComposable(
        series = series(),
        expanded = false,
        loading = false,
        books = listOf(book("b1", "1")),
        imageLoader = imageLoader,
        navController = navController,
        onToggle = {},
        onPrefetch = {},
      )
    }

    composeRule.onNodeWithTag("bookItem_b1").assertDoesNotExist()
  }

  @Test
  fun expandedSeriesRendersItsBooks() {
    composeRule.setContent {
      SeriesComposable(
        series = series(),
        expanded = true,
        loading = false,
        books = listOf(book("b1", "1"), book("b2", "2")),
        imageLoader = imageLoader,
        navController = navController,
        onToggle = {},
        onPrefetch = {},
      )
    }

    composeRule.onNodeWithTag("bookItem_b1").assertExists()
    composeRule.onNodeWithTag("bookItem_b2").assertExists()
  }
}
