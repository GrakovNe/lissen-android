package org.grakovne.lissen.minifiedtest

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class PlayerBookmarksDownloadsE2ETest {
  @Test
  fun player_bookmark_createThenDeleteRoundTrip() = loggedInApp {
    openFirstBook()
    clickUntil(By.res("playerBookmarksButton"), By.text("Bookmarks"))

    clickElement(By.text("Create bookmark"))
    setTextOf(By.clazz("android.widget.EditText"), "E2E bookmark")
    // the sheet submits through the trailing add-icon of the editing row; the IME action
    // would work too but is not reliably reachable from uiautomator
    rightmostButtonAlignedWith(By.clazz("android.widget.EditText")).click()

    assertTrue(
      "the created bookmark should appear in the sheet",
      elementExists(By.text("E2E bookmark"), 15_000),
    )

    rightmostButtonAlignedWith(By.text("E2E bookmark")).click()

    waitUntilAbsent(By.text("E2E bookmark"))
  }

  @Test
  fun player_downloads_currentChapterRoundTrip() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Downloads"), By.text("Download book"))
    clickElement(By.text("Current chapter"))

    // a tiny chapter may finish before the sheet is reopened, so either the stop action
    // (still downloading) or the clear action (finished) proves the request took effect
    val outcome = waitForAnyText("Stop downloading", "Clear downloaded chapters")
    clickUntil(By.text("Downloads"), By.text("Download book"))
    scrollUntilVisible(By.text(outcome))
    clickElement(By.text(outcome))
    waitUntilAbsent(By.text(outcome))
  }

  @Test
  fun player_downloads_chapterCountStepperRoundTrip() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Downloads"), By.text("Download book"))
    scrollUntilVisible(By.desc("Increase count"))

    val before = stepperCount()
    clickElement(By.desc("Increase count"))
    assertEquals("the stepper should show one more chapter", before + 1, stepperCount())
    clickElement(By.desc("Decrease count"))
    assertEquals("the stepper should be back at the original count", before, stepperCount())
  }

  @Test
  fun settings_volumeBoost_presetRoundTrip() = loggedInApp {
    openSettings()
    clickElement(By.text("Playback"))
    waitForElement(By.text("Boosted volume"))
    assertEquals(
      "the boost row should say disabled by default",
      "Disabled",
      subtitleUnder("Boosted volume"),
    )

    clickUntil(By.text("Boosted volume"), By.text("6"))
    tapPresetButton("6")
    pressBack()
    assertEquals("the row should show the chosen boost", "6 dB", subtitleUnder("Boosted volume"))

    clickUntil(By.text("Boosted volume"), By.text("0"))
    tapPresetButton("0")
    pressBack()
    assertEquals("the row should be disabled again", "Disabled", subtitleUnder("Boosted volume"))
  }

  /** The rightmost button whose center falls inside the bounds of the first [reference] node. */
  private fun UiAutomatorTestScope.rightmostButtonAlignedWith(reference: BySelector) =
    alignedButtons(reference).maxByOrNull { it.visibleBounds.centerX() }
      ?: throw AssertionError("no button aligned with $reference")

  private fun UiAutomatorTestScope.alignedButtons(reference: BySelector): List<androidx.test.uiautomator.UiObject2> {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val node = device.findObjects(reference).firstOrNull()
      if (node != null) {
        val bounds = node.visibleBounds
        val buttons =
          device
            .findObjects(By.clazz("android.widget.Button"))
            .filter { bounds.contains(it.visibleBounds.centerX(), it.visibleBounds.centerY()) }
        if (buttons.isNotEmpty()) return buttons
      }
      Thread.sleep(300)
    }
    throw AssertionError("no button aligned with $reference appeared within ${DEFAULT_TIMEOUT_MS}ms")
  }

  private fun UiAutomatorTestScope.stepperCount(): Int {
    val row = device.findObjects(By.text("Next chapters")).first().visibleBounds
    val numbers =
      device
        .findObjects(By.clazz("android.widget.EditText"))
        .filter { row.contains(it.visibleBounds.centerX(), it.visibleBounds.centerY()) }
        .mapNotNull { it.text.toString().trim().toIntOrNull() }
    assertTrue("the stepper should show a count on the Next chapters row", numbers.isNotEmpty())
    return numbers.first()
  }

  /** The text of the line directly under the row titled [title]. */
  private fun UiAutomatorTestScope.subtitleUnder(title: String): String {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val heading = device.findObjects(By.text(title)).firstOrNull()
      if (heading != null) {
        val head = heading.visibleBounds
        val band = Rect(head.left, head.bottom, head.right, head.bottom + 120)
        val sub =
          device
            .findObjects(By.text(Pattern.compile(".+")))
            .firstOrNull { band.contains(it.visibleBounds.centerX(), it.visibleBounds.centerY()) }
        if (sub != null) return sub.text.toString()
      }
      Thread.sleep(300)
    }
    throw AssertionError("no subtitle line found under '$title'")
  }

  private fun UiAutomatorTestScope.waitForAnyText(vararg texts: String): String {
    val deadline = System.currentTimeMillis() + 60_000
    while (System.currentTimeMillis() < deadline) {
      texts.firstOrNull { elementExists(By.text(it), 1_000) }?.let { return it }
    }
    throw AssertionError("none of ${texts.toList()} appeared within 60s")
  }

  private fun UiAutomatorTestScope.openSettings() {
    for (attempt in 0 until 3) {
      if (elementExists(By.res("settingsButton"))) {
        clickElement(By.res("settingsButton"))
      } else if (elementExists(By.desc("Settings"))) {
        clickElement(By.desc("Settings"))
      } else {
        device.pressBack()
      }
      if (elementExists(By.text("Connection"), 10_000)) return
    }
    waitForElement(By.text("Connection"))
  }

  private fun UiAutomatorTestScope.tapPresetButton(text: String) {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val buttons = device.findObjects(By.clazz("android.widget.Button"))
      val button =
        device
          .findObjects(By.text(text))
          .mapNotNull { caption ->
            buttons.firstOrNull { it.visibleBounds.contains(caption.visibleBounds.centerX(), caption.visibleBounds.centerY()) }
          }.firstOrNull()
      if (button != null) {
        button.click()
        return
      }
      Thread.sleep(300)
    }
    throw AssertionError("no preset button with caption '$text' found")
  }
}
