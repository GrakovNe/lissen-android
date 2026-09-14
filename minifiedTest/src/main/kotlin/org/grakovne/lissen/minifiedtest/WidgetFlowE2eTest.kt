package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.UiObject2
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WidgetFlowE2eTest {
  @Test
  fun a_stateWidgetPlaceholderTapOpensApp() =
    freshApp {
      val freshlyPinned = pinWidget(STATE_WIDGET_LABEL)
      val host = widgetHost(STATE_WIDGET_LABEL)
      if (freshlyPinned) {
        waitForIn(host, By.text(PLACEHOLDER_TEXT))
      }
      (host.findObject(By.text(PLACEHOLDER_TEXT)) ?: host).click()
      waitForAppToBeVisible(TARGET_PACKAGE)
      waitForElement(By.res("hostInput"), DEFAULT_TIMEOUT_MS)
    }

  @Test
  fun b_stateWidgetReflectsPlaybackAndControlsIt() =
    freshApp {
      pinWidget(STATE_WIDGET_LABEL)

      startApp(TARGET_PACKAGE)
      loginToLibrary()
      openFirstBook()
      clickElement(By.desc("Play"))
      waitForElement(By.desc("Pause"))
      assertEquals("media session should report PLAYING", "PLAYING(3)", mediaSessionState())

      device.pressHome()
      val host = widgetHost(STATE_WIDGET_LABEL)
      // The widget re-renders with the pause control once playback state propagates
      waitForIn(host, By.desc("Pause")).click()
      awaitMediaSessionState("PAUSED(2)")

      waitForIn(host, By.desc("Play")).click()
      awaitMediaSessionState("PLAYING(3)")
    }

  @Test
  fun c_coverWidgetControlsPlayback() =
    freshApp {
      // Pin before playback starts: a freshly bound widget only renders the
      // current playback state after the next state change
      pinWidget(COVER_WIDGET_LABEL)

      startApp(TARGET_PACKAGE)
      loginToLibrary()
      openFirstBook()
      clickElement(By.desc("Play"))
      waitForElement(By.desc("Pause"))

      device.pressHome()
      val host = widgetHost(COVER_WIDGET_LABEL)
      // The widget re-renders with the pause control once playback state propagates
      waitForIn(host, By.desc("Pause")).click()
      awaitMediaSessionState("PAUSED(2)")

      waitForIn(host, By.desc("Play")).click()
      awaitMediaSessionState("PLAYING(3)")
    }

  private fun waitForIn(
    scope: UiObject2,
    selector: BySelector,
    timeoutMs: Long = WIDGET_WAIT_MS,
  ): UiObject2 {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      scope.findObject(selector)?.let { return it }
      Thread.sleep(300)
    }
    throw AssertionError("No element matching $selector inside widget within ${timeoutMs}ms")
  }

  private companion object {
    const val PLACEHOLDER_TEXT = "Click to open the app"
  }
}
