package org.grakovne.lissen.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.junit.Rule
import org.junit.Test

class AsyncShimmeringImageTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun request() = ImageRequest.Builder(context).data("missing://cover").build()

  @Test
  fun rendersWithContentDescription() {
    composeRule.setContent {
      AsyncShimmeringImage(
        imageRequest = request(),
        imageLoader = ImageLoader.Builder(context).build(),
        contentDescription = "book cover",
        contentScale = ContentScale.Fit,
        error = ColorPainter(Color.Gray),
      )
    }

    composeRule.onNodeWithContentDescription("book cover").assertExists()
  }

  @Test
  fun startsInLoadingState() {
    val states = mutableListOf<Boolean>()

    composeRule.setContent {
      AsyncShimmeringImage(
        imageRequest = request(),
        imageLoader = ImageLoader.Builder(context).build(),
        contentDescription = "book cover",
        contentScale = ContentScale.Fit,
        error = ColorPainter(Color.Gray),
        onLoadingStateChanged = { states += it },
      )
    }

    composeRule.waitForIdle()

    assert(states.firstOrNull() == true) { "expected initial loading=true, got $states" }
  }
}
