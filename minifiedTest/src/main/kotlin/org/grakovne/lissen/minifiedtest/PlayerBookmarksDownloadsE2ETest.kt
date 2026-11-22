package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.UiObject2
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
    // the row submits through its trailing add-icon; the IME action would work too but is not
    // reliably reachable from uiautomator
    rightmostButtonOnRowOf(By.text("Create bookmark")).click()

    assertTrue(
      "the created bookmark should appear in the sheet",
      elementExists(By.text("E2E bookmark"), 15_000),
    )

    rightmostButtonOnRowOf(By.text("E2E bookmark")).click()

    waitUntilAbsent(By.text("E2E bookmark"))
  }

  @Test
  fun player_downloads_currentChapterRoundTrip() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Downloads"), By.text("Download book"))
    clickElement(By.text("Current chapter"))
    // the option dismisses the sheet, so nothing can be observed until it is reopened

    // a tiny chapter may finish before the sheet is reopened, so either the stop action (still
    // downloading) or the clear action (finished) proves the request took effect
    clickUntil(By.text("Downloads"), By.text("Download book"))
    val outcome = waitForAnyTextInSheet("Stop downloading", "Clear downloaded chapters")
    scrollUntilVisible(By.text(outcome))
    clickElement(By.text(outcome))

    // reopened on purpose: the option must be gone from an OPEN sheet, otherwise its absence
    // would only prove the sheet closed
    clickUntil(By.text("Downloads"), By.text("Download book"))
    waitUntilAbsent(By.text(outcome))
  }

  @Test
  fun player_downloads_chapterCountStepperRoundTrip() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Downloads"), By.text("Download book"))
    scrollUntilVisible(By.desc("Increase count"))

    // either direction proves the stepper moves the count; which one is enabled depends on how
    // many chapters the book has left, so try up first and fall back to down
    val before = stepperCount()
    clickElement(By.desc("Increase count"))
    val afterUp = stepperCount()

    if (afterUp == before + 1) {
      clickElement(By.desc("Decrease count"))
    } else {
      assertEquals("increase should move the count by one, or be disabled at the maximum", before, afterUp)
      clickElement(By.desc("Decrease count"))
      assertEquals("the stepper should show one fewer chapter", before - 1, stepperCount())
      clickElement(By.desc("Increase count"))
    }

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

  /**
   * Buttons that vertically overlap the first [reference] node, i.e. sit on its row. Overlap
   * rather than a fixed pixel tolerance: it holds at any display density and cannot bleed into
   * the neighbouring row.
   */
  private fun UiAutomatorTestScope.buttonsOnRowOf(reference: BySelector): List<UiObject2> {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val node = device.findObjects(reference).firstOrNull()
      if (node != null) {
        val band = node.visibleBounds
        val row =
          device
            .findObjects(By.clazz("android.widget.Button"))
            .filter { it.visibleBounds.top < band.bottom && band.top < it.visibleBounds.bottom }
        if (row.isNotEmpty()) return row
      }
      Thread.sleep(300)
    }
    throw AssertionError("no button on the row of $reference appeared within ${DEFAULT_TIMEOUT_MS}ms")
  }

  private fun UiAutomatorTestScope.rightmostButtonOnRowOf(reference: BySelector) =
    buttonsOnRowOf(reference).maxByOrNull { it.visibleBounds.centerX() }
      ?: throw AssertionError("no button on the row of $reference")

  private fun UiAutomatorTestScope.stepperCount(): Int {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val row = device.findObjects(By.text("Next chapters")).firstOrNull()
      if (row != null) {
        val band = row.visibleBounds
        // the count is a plain Text node between the two stepper buttons (it only becomes an
        // EditText while being typed into); the row label is text too, so keep only what parses
        val numbers =
          device
            .findObjects(By.clazz("android.widget.TextView"))
            .filter { it.visibleBounds.top < band.bottom && band.top < it.visibleBounds.bottom }
            .mapNotNull { it.text?.trim()?.toIntOrNull() }
        if (numbers.isNotEmpty()) return numbers.first()
      }
      Thread.sleep(300)
    }
    throw AssertionError("the Next chapters row never showed a numeric count")
  }

  /** The text of the line directly under the row titled [title], matched by its left edge. */
  private fun UiAutomatorTestScope.subtitleUnder(title: String): String {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val heading = device.findObjects(By.text(title)).firstOrNull()
      if (heading != null) {
        val head = heading.visibleBounds
        // the supporting line shares the heading's left padding, so match on that and take the
        // closest line below it rather than any text that happens to sit lower on screen
        val leftTolerance = (head.height() / 4).coerceAtLeast(8)
        val below =
          device
            .findObjects(By.text(Pattern.compile(".+")))
            .filter { kotlin.math.abs(it.visibleBounds.left - head.left) <= leftTolerance }
            .filter { it.visibleBounds.top >= head.bottom - 4 }
            .minByOrNull { it.visibleBounds.top }
        if (below != null) return below.text.toString()
      }
      Thread.sleep(300)
    }
    throw AssertionError("no subtitle line found under '$title'")
  }

  /**
   * Waits for one of [texts] while the sheet is open. The download option dismisses the sheet,
   * so the caller must reopen it first; a text that never appears stays a failure rather than
   * silently passing.
   */
  private fun UiAutomatorTestScope.waitForAnyTextInSheet(vararg texts: String): String {
    val deadline = System.currentTimeMillis() + 60_000
    while (System.currentTimeMillis() < deadline) {
      texts.firstOrNull { elementExists(By.text(it), 1_000) }?.let { return it }
      if (!elementExists(By.text("Download book"), 1_000)) {
        throw AssertionError("the downloads sheet closed while waiting for ${texts.toList()}")
      }
    }
    throw AssertionError("none of ${texts.toList()} appeared within 60s")
  }

  private fun UiAutomatorTestScope.openSettings() {
    clickUntil(By.desc("Menu"), By.text("Application settings"))
    clickElement(By.text("Application settings"))
    waitForElement(By.res("settingsScreen"))
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
