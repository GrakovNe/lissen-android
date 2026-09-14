package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

      val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
      while (System.currentTimeMillis() < deadline && !shortcutDump().contains(SHORTCUT_ID)) {
        Thread.sleep(500)
      }
      assertTrue(
        "dynamic shortcut $SHORTCUT_ID missing from shortcuts dump",
        shortcutDump().contains(SHORTCUT_ID),
      )
    }

  @Test
  fun shortcut_tapFromAppDrawerOpensPlayer() =
    freshApp {
      loginToLibrary()
      openFirstBook()
      clickElement(By.desc("Play"))
      waitForElement(By.desc("Pause"))

      device.pressHome()
      openAppDrawer()
      longPressOn(scrollUntilVisible(By.text("Lissen"), 12))
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
