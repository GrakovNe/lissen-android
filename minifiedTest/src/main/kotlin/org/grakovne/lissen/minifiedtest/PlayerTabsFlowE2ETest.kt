package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class PlayerTabsFlowE2ETest {
  @Test
  fun player_chaptersTab_selectingChapterChangesCurrent() = loggedInApp {
    openFirstBook()
    val current = chapterNumber()
    val row = device
      .findObjects(By.text(ROW))
      .firstOrNull { leadingNumber(it.text.toString()) != current }
    assertNotNull("expected a chapter row other than the current one", row)
    val target = leadingNumber(row!!.text.toString())
    row.click()
    assertEquals(target, awaitChapterNumber(current))
  }

  @Test
  fun player_speedTab_selectingSpeedIsReflected() = loggedInApp {
    openFirstBook()
    clickElement(By.text("Speed"))
    waitForElement(By.text("Playback speed"))
    clickElement(By.text("1.5"))
    assertEquals("1.50x", speedCurrent())
  }

  @Test
  fun player_timerTab_selectingPresetIsReflected() = loggedInApp {
    openFirstBook()
    clickElement(By.text("Timer"))
    waitForElement(By.text("Sleep Timer"))
    clickElement(By.text("15"))
    assertTrue("selecting 15 should show '15 minutes'", elementExists(By.text("15 minutes")))
  }

  @Test
  fun player_downloadsTab_showsDownloadOptions() = loggedInApp {
    openFirstBook()
    clickElement(By.text("Downloads"))
    waitForElement(By.text("Download book"))
    assertTrue(elementExists(By.text("Entire book")))
  }

  @Test
  fun player_bookmarksButton_opensBookmarksSheet() = loggedInApp {
    openFirstBook()
    clickElement(By.res("playerBookmarksButton"))
    waitForElement(By.text("Bookmarks"))
    assertTrue("bookmarks sheet should offer a create action", elementExists(By.text("Create bookmark")))
  }

  private fun UiAutomatorTestScope.chapterNumber(): Int {
    val text = waitForElement(By.res("playerChapterNumber"), 10_000).text.toString()
    return Regex("Chapter (\\d+) of").find(text)?.groupValues?.get(1)?.toInt() ?: -1
  }

  private fun UiAutomatorTestScope.awaitChapterNumber(from: Int): Int {
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
      val n = device.findObject(By.res("playerChapterNumber"))?.text?.toString()
        ?.let { Regex("Chapter (\\d+) of").find(it)?.groupValues?.get(1)?.toInt() }
      if (n != null && n != from) return n
      Thread.sleep(300)
    }
    return chapterNumber()
  }

  private fun UiAutomatorTestScope.speedCurrent(): String =
    waitForElement(By.text(Pattern.compile("^\\d\\.\\d\\dx$")), 10_000).text.toString()

  private fun leadingNumber(text: String): Int =
    Regex("^(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: -1

  companion object {
    val ROW = Pattern.compile("^\\d+\\s-\\s.*")
  }
}
