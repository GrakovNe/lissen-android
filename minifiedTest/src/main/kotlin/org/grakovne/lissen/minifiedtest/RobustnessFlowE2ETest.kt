package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobustnessFlowE2ETest {
  @Test
  fun playback_survivesRotation() = loggedInApp {
    openFirstBook()
    assertTrue("play controls present before rotation", controlsPresent())
    device.setOrientationLeft()
    Thread.sleep(1500)
    assertTrue("play controls present after rotation", controlsPresent())
    device.setOrientationNatural()
    Thread.sleep(1000)
    assertTrue("play controls present after rotating back", controlsPresent())
  }

  private fun UiAutomatorTestScope.controlsPresent(): Boolean =
    elementExists(By.desc("Play")) || elementExists(By.desc("Pause"))
}
