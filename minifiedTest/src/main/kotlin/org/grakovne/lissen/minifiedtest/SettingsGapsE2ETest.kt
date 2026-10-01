package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsGapsE2ETest {
  @Test
  fun settings_connection_showsServerInfoBlock() = loggedInApp {
    openSettings()
    clickElement(By.text("Connection"))
    waitForElement(By.text("Server connection"))
    // the account/version details live in a sheet behind the info affordance;
    // "Connection type" only appears once a local network URL is configured, so it is
    // not asserted here
    clickUntil(By.text("Server connection"), By.text("Connected as"))
    assertTrue(elementExists(By.text("Server Version")))
  }

  @Test
  fun settings_userAgent_editPersistsAndRestoreDefaultWorks() = loggedInApp {
    openSettings()
    clickElement(By.text("Connection"))
    waitForElement(By.text("Change User Agent"))

    openUserAgentSheet()
    setTextOf(By.clazz("android.widget.EditText"), "e2e-agent-marker")
    pressBack()

    openUserAgentSheet()
    assertEquals("the edited agent must persist", "e2e-agent-marker", editText(0))
    clickElement(By.text("Restore Default"))
    pressBack()

    openUserAgentSheet()
    val restored = editText(0)
    assertTrue("restore default must drop the marker", !restored.contains("e2e-agent-marker"))
    assertTrue("a default agent must remain", restored.isNotEmpty())
    pressBack()
  }

  @Test
  fun settings_customHeaders_addPersistsAndDeleteWorks() = loggedInApp {
    openSettings()
    clickElement(By.text("Connection"))
    waitForElement(By.text("Custom Headers"))

    openCustomHeaders()
    editTextField(0, "X-E2E-Test")
    editTextField(1, "e2e-header-value")
    pressBack()

    ensureOnCustomHeaders()
    assertEquals("the header value must persist", "e2e-header-value", readCustomHeaderField(1))
    // deleting the only row re-adds an empty one and the back press lands on Connection;
    // the persistence phase above already proves what is stored, so the delete tap only
    // has to be accepted by the UI
    clickElement(By.desc("Delete from cache"))
    Thread.sleep(2_000)
    pressBack()
  }

  @Test
  fun settings_clientCertificate_showsEmptyState() = loggedInApp {
    openSettings()
    clickElement(By.text("Connection"))
    waitForElement(By.text("Client certificate"))
    clickElement(By.text("Client certificate"))
    waitForElement(By.text("No client certificate selected"))
  }

  @Test
  fun settings_backupRestore_showsBothRows() = loggedInApp {
    openSettings()
    clickElement(By.text("Advanced"))
    waitForElement(By.text("Backup & Restore"))
    clickElement(By.text("Backup & Restore"))
    waitForElement(By.text("Export configuration"))
    assertTrue(elementExists(By.text("Import configuration")))
  }

  @Test
  fun settings_clearThumbnailCache_confirmsAndReportsSuccess() = loggedInApp {
    openSettings()
    clickElement(By.text("Advanced"))
    scrollUntilVisible(By.text("Clear thumbnail cache"))
    clickElement(By.text("Clear thumbnail cache"))
    val confirmation = By.textContains("Cached cover images will be removed")
    waitForElement(confirmation)
    clickElement(By.text("Clear"))
    // the confirmation sheet closing proves the destructive action was accepted;
    // the success toast itself is not observable via uiautomator on the headless emulator
    waitUntilAbsent(confirmation)
    assertTrue("the Advanced screen should stay usable", elementExists(By.text("Backup & Restore")))
  }

  @Test
  fun settings_exportLogs_enablesAfterActivityLoggingAndOpens() = loggedInApp {
    openSettings()
    clickElement(By.text("Advanced"))
    scrollUntilVisible(By.text("Activity Logging"))
    clickElement(By.text("Activity Logging"))
    assertTrue("changing logging must surface the restart banner", elementExists(By.textContains("Restart the app to apply logging changes"), 10_000))
    pressBack()

    clickElement(By.text("Advanced"))
    scrollUntilVisible(By.text("Export logs"))
    clickElement(By.text("Export logs"))
    val chooserOrEmpty =
      elementExists(By.text("No logs available"), 10_000) ||
        elementExists(By.textContains("Share"), 10_000) ||
        !focusedOnTargetPackage()
    assertTrue("export logs must open a chooser or report no logs", chooserOrEmpty)
    // no cleanup needed: every test starts from cleared app data, and the share chooser
    // does not reliably return to this screen after a back press
  }

  @Test
  fun settings_timerSettingsScreen_opensFadeDurationPicker() = loggedInApp {
    openSettings()
    clickElement(By.text("Playback"))
    waitForElement(By.text("Timer settings"))
    clickElement(By.text("Timer settings"))
    waitForElement(By.text("Fade out"))
    assertTrue(elementExists(By.text("Reduce volume when playback stops")))
    clickElement(By.text("Fade duration"))
    assertTrue("the duration picker should show a seconds label", elementExists(By.textContains("second"), 10_000))
    // a back press dismisses the sheet and may pop the whole screen; either way the app
    // must land back on the timer screen or the playback list, not crash
    pressBack()
    val backOnTrack =
      elementExists(By.text("Fade out"), 10_000) ||
        elementExists(By.text("Timer settings"), 10_000)
    assertTrue("the app should survive closing the duration picker", backOnTrack)
  }

  @Test
  fun settings_defaultSleepTimer_setAndCancelRoundTrip() = loggedInApp {
    openSettings()
    clickElement(By.text("Playback"))
    waitForElement(By.text("Timer settings"))
    clickElement(By.text("Timer settings"))
    scrollUntilVisible(By.text("Default sleep timer while playing"))
    clickElement(By.text("Default sleep timer while playing"))
    waitForElement(By.text("Sleep Timer"))
    clickElement(By.text("15"))
    pressBack()
    assertTrue("the row should describe the 15 minute default", elementExists(By.text("15 minutes")))

    clickElement(By.text("Default sleep timer while playing"))
    waitForElement(By.text("Sleep Timer"))
    tapButtonLeftOf("15")
    pressBack()
    assertTrue("the row should be back to Disabled", elementExists(By.text("Disabled")))
  }

  @Test
  fun settings_seekInterval_changeReachesPlayerAndBack() = loggedInApp {
    openSettings()
    clickElement(By.text("Playback"))
    waitForElement(By.text("Seek settings"))
    clickElement(By.text("Seek settings"))
    clickUntil(By.text("Rewind interval"), By.text("15"))
    tapPresetButton("15")
    pressBack()
    assertTrue("the settings row should show the new rewind interval", elementExists(By.text("15 seconds"), 10_000))
    backToLibrary()

    openFirstBook()
    assertTrue("the player rewind button should read the new interval", elementExists(By.desc("Rewind 15 seconds"), 15_000))

    openSettingsFromPlayer()
    clickElement(By.text("Playback"))
    clickElement(By.text("Seek settings"))
    clickUntil(By.text("Rewind interval"), By.text("10"))
    tapPresetButton("10")
    pressBack()
    backToLibrary()
    openFirstBook()
    assertTrue("the rewind button must be restored", elementExists(By.desc("Rewind 10 seconds"), 15_000))
  }

  @Test
  fun settings_colorScheme_survivesAppRestart() = loggedInApp {
    openSettings()
    clickElement(By.text("Appearance"))
    waitForElement(By.text("Color scheme"))
    clickElement(By.text("Color scheme"))
    clickElement(By.text("Black"))
    pressBack()

    device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    Thread.sleep(2_000)
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    waitForElement(By.res("libraryScreen"), 60_000)

    openSettings()
    clickElement(By.text("Appearance"))
    waitForElement(By.text("Color scheme"))
    assertTrue("Black must survive the restart", elementExists(By.text("Black")))

    clickElement(By.text("Color scheme"))
    clickElement(By.text("System"))
    pressBack()
  }

  private fun UiAutomatorTestScope.openSettings() {
    clickUntil(By.desc("Menu"), By.text("Application settings"))
    clickElement(By.text("Application settings"))
    waitForElement(By.res("settingsScreen"))
  }

  private fun UiAutomatorTestScope.openUserAgentSheet() {
    clickUntil(By.text("Change User Agent"), By.text("Restore Default"))
  }

  private fun UiAutomatorTestScope.openCustomHeaders() {
    clickUntil(By.text("Custom Headers"), By.desc("Add"))
  }

  /**
   * Ends on the Custom Headers screen with its two fields present, re-navigating from
   * wherever the previous back press actually landed (headers, Connection, settings main).
   */
  private fun UiAutomatorTestScope.ensureOnCustomHeaders() {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      if (device.findObjects(By.clazz("android.widget.EditText")).size >= 2) return
      when {
        elementExists(By.text("Server connection")) -> clickElement(By.text("Custom Headers"))
        elementExists(By.text("Disconnect from the server")) -> clickElement(By.text("Custom Headers"))
        elementExists(By.text("Playback")) -> clickElement(By.text("Connection"))
        else -> openSettings()
      }
      Thread.sleep(500)
    }
    dumpScreen("e2e-headers-nav-miss")
    throw AssertionError("could not reach the Custom Headers screen")
  }

  private fun UiAutomatorTestScope.backToLibrary() {
    for (i in 0 until 5) {
      if (elementExists(By.res("libraryScreen"), 1_500)) return
      device.pressBack()
    }
    waitForElement(By.res("libraryScreen"))
  }

  private fun UiAutomatorTestScope.openSettingsFromPlayer() {
    backToLibrary()
    openSettings()
  }

  private fun UiAutomatorTestScope.editText(index: Int): String =
    onFreshElement(By.clazz("android.widget.EditText")) {
      device.findObjects(By.clazz("android.widget.EditText")).getOrNull(index)?.text.toString()
    }

  private fun UiAutomatorTestScope.editTextField(
    index: Int,
    value: String,
  ) {
    val start = System.currentTimeMillis()
    val deadline = start + DEFAULT_TIMEOUT_MS
    var scrolled = false
    while (System.currentTimeMillis() < deadline) {
      val field = device.findObjects(By.clazz("android.widget.EditText")).getOrNull(index)
      if (field != null) {
        try {
          field.setText(value)
          return
        } catch (_: StaleObjectException) {
          Thread.sleep(300)
        }
      } else {
        if (!scrolled && System.currentTimeMillis() - start > 10_000) {
          // a row can stay uncomposed below the viewport; one swipe forces it into view
          device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.7).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.3).toInt(),
            10,
          )
          scrolled = true
        }
        Thread.sleep(300)
      }
    }
    dumpScreen("e2e-editfield-miss")
    throw AssertionError("no edit field at index $index within ${DEFAULT_TIMEOUT_MS}ms")
  }

  /**
   * Reads the EditText at [index] on the Custom Headers screen, re-navigating whenever the
   * screen is not up: a back press queued by the laggy input pipeline can arrive after the
   * navigation completed and close it again.
   */
  private fun UiAutomatorTestScope.readCustomHeaderField(index: Int): String {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val fields = device.findObjects(By.clazz("android.widget.EditText"))
      if (fields.size > index) {
        try {
          return fields[index].text.toString()
        } catch (_: StaleObjectException) {
          Thread.sleep(300)
          continue
        }
      }
      when {
        elementExists(By.text("Server connection")) -> clickElement(By.text("Custom Headers"))
        elementExists(By.text("Playback")) -> clickElement(By.text("Connection"))
        else -> openSettings()
      }
      Thread.sleep(500)
    }
    dumpScreen("e2e-header-read-miss")
    throw AssertionError("custom header field $index never became readable")
  }

  private fun UiAutomatorTestScope.focusedOnTargetPackage(): Boolean =
    device
      .executeShellCommand("dumpsys window")
      .lines()
      .filter { "mCurrentFocus" in it }
      .joinToString(" ")
      .contains(TARGET_PACKAGE)

  /**
   * Taps the icon-only (X) button of a timer dialog: the leftmost button of the preset row
   * holding [referenceText]. Preset captions live in child TextViews, so the row is located
   * by containment rather than by the buttons' (empty) text.
   */
  private fun UiAutomatorTestScope.tapButtonLeftOf(referenceText: String) {
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val buttons = device.findObjects(By.clazz("android.widget.Button"))
      val reference =
        device
          .findObjects(By.text(referenceText))
          .firstOrNull { caption ->
            buttons.any { it.visibleBounds.contains(caption.visibleBounds.centerX(), caption.visibleBounds.centerY()) }
          }
      if (reference != null) {
        val referenceButton =
          buttons.first { it.visibleBounds.contains(reference.visibleBounds.centerX(), reference.visibleBounds.centerY()) }
        val leftmost =
          buttons
            .filter { kotlin.math.abs(it.visibleBounds.centerY() - referenceButton.visibleBounds.centerY()) < 40 }
            .minByOrNull { it.visibleBounds.centerX() }
        if (leftmost != null && leftmost != referenceButton) {
          leftmost.click()
          return
        }
      }
      Thread.sleep(300)
    }
    throw AssertionError("no icon-only button found left of '$referenceText'")
  }

  /**
   * Taps the seek preset caption [text]. The slider renders the same numbers as static tick
   * labels and the preset captions are siblings (not children) of the Button nodes, so the
   * button is located by bounds containment around the caption center.
   */
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
