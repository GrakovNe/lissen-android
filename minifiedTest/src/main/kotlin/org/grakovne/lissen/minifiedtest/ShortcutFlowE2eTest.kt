package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutFlowE2eTest {
  @Test
  fun shortcut_registeredOnlyAfterPlaybackStarts() =
    freshApp {
      loginToLibrary()
      assertFalse(
        "shortcut must not exist before any playback",
        shortcutDump().contains(SHORTCUT_ID),
      )

      openFirstBook()
      clickElement(By.desc("Play"))
      waitForElement(By.desc("Pause"))
      awaitShortcutRegistered(SHORTCUT_ID)
    }

  @Test
  fun shortcut_tapFromAppDrawerOpensPlayer() =
    freshApp {
      loginToLibrary()
      openFirstBook()
      clickElement(By.desc("Play"))
      waitForElement(By.desc("Pause"))
      awaitShortcutRegistered(SHORTCUT_ID)

      device.pressHome()
      openAppDrawer()
      scrollUntilVisible(By.text("Lissen"), 12)
      // The launcher may serve a stale popup right after registration, and a
      // long-press occasionally misses; retry until the entry shows up
      var attempts = 0
      while (device.findObject(By.text(SHORTCUT_LABEL)) == null) {
        if (++attempts > 3) throw AssertionError("popup never showed '$SHORTCUT_LABEL'")
        if (device.findObject(By.text("App info")) != null) device.pressBack()
        if (device.findObject(By.text("Lissen")) == null) openAppDrawer()
        longPressOn(waitForElement(By.text("Lissen")))
      }
      clickElement(By.text(SHORTCUT_LABEL), LAUNCHER_WAIT_MS)

      waitForElement(By.res("playerScreen"), DEFAULT_TIMEOUT_MS)
      assertEquals("playback should survive the shortcut round trip", "PLAYING(3)", mediaSessionState())
    }

  @Test
  fun shortcut_intentPayloadOpensPlayer() =
    freshApp {
      loginToLibrary()
      openFirstBook()
      clickElement(By.desc("Play"))
      waitForElement(By.desc("Pause"))

      device.pressHome()
      device.executeShellCommand(
        "am start -a continue_playback -n $TARGET_PACKAGE/org.grakovne.lissen.ui.activity.AppActivity",
      )

      waitForElement(By.res("playerScreen"), DEFAULT_TIMEOUT_MS)
      assertEquals("playback should survive the intent round trip", "PLAYING(3)", mediaSessionState())
    }

  private companion object {
    const val SHORTCUT_ID = "continue_playback_shortcut"
    const val SHORTCUT_LABEL = "Continue listening"
  }
}
