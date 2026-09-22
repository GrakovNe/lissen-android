package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowE2ETest {
  @Test
  fun settings_screenListsAllCategories() = loggedInApp {
    openSettings()
    for (category in CATEGORIES) {
      assertTrue("missing settings category: $category", elementExists(By.text(category)))
    }
  }

  @Test
  fun settings_openAppearance_showsColorScheme() = loggedInApp {
    openSettings()
    clickElement(By.text("Appearance"))
    waitForElement(By.text("Color scheme"))
    assertTrue("Appearance should show Material You toggle", elementExists(By.text("Material You colors")))
  }

  @Test
  fun settings_openPlayback_showsControls() = loggedInApp {
    openSettings()
    clickElement(By.text("Playback"))
    waitForElement(By.text("Boosted volume"))
    assertTrue(elementExists(By.text("Seek settings")))
    assertTrue(elementExists(By.text("Timer settings")))
  }

  @Test
  fun settings_openDownloads_showsControls() = loggedInApp {
    openSettings()
    clickElement(By.text("Downloads"))
    waitForElement(By.text("Download automatically"))
    assertTrue(elementExists(By.text("Storage location")))
  }

  @Test
  fun settings_openAdvanced_showsControls() = loggedInApp {
    openSettings()
    clickElement(By.text("Advanced"))
    waitForElement(By.text("Crash reporting"))
    assertTrue(elementExists(By.text("Activity Logging")))
    assertTrue(elementExists(By.text("Backup & Restore")))
  }

  @Test
  fun settings_colorScheme_selectionIsReflected() = loggedInApp {
    openSettings()
    clickElement(By.text("Appearance"))
    waitForElement(By.text("Color scheme"))
    clickElement(By.text("Color scheme"))
    waitForElement(By.text("Black"))
    clickElement(By.text("Black"))
    backToTopLevel()
    clickElement(By.text("Appearance"))
    waitForElement(By.text("Color scheme"))
    assertTrue("Color scheme should now show Black", elementExists(By.text("Black")))
    assertTrue("previous System value should be gone", !elementExists(By.text("System")))
  }

  private fun UiAutomatorTestScope.openSettings() {
    clickUntil(By.desc("Menu"), By.text("Application settings"))
    clickElement(By.text("Application settings"))
    waitForElement(By.res("settingsScreen"))
  }

  private fun UiAutomatorTestScope.backToTopLevel() {
    for (i in 0 until 4) {
      if (CATEGORIES.all { elementExists(By.text(it), 1200) }) return
      device.pressBack()
      Thread.sleep(700)
    }
  }

  private companion object {
    val CATEGORIES = listOf("Connection", "Playback", "Appearance", "Downloads", "Advanced")
  }
}
