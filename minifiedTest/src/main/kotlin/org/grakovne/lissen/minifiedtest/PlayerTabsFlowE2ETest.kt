package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
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
    val target = tapChapterRowOtherThan(current)
    assertNotNull("expected a chapter row other than the current one", target)
    assertEquals(target, awaitChapterNumber(current))
  }

  @Test
  fun player_speedTab_selectingSpeedIsReflected() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Speed"), By.text("Playback speed"))
    clickElement(By.text("1.5"))
    assertEquals("1.50x", speedCurrent())
  }

  @Test
  fun player_timerTab_selectingPresetIsReflected() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Timer"), By.text("Sleep Timer"))
    clickElement(By.text("15"))
    assertTrue("selecting 15 should show '15 minutes'", elementExists(By.text("15 minutes")))
  }

  @Test
  fun player_downloadsTab_showsDownloadOptions() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Downloads"), By.text("Download book"))
    assertTrue(elementExists(By.text("Entire book")))
  }

  @Test
  fun player_bookmarksButton_opensBookmarksSheet() = loggedInApp {
    openFirstBook()
    clickUntil(By.res("playerBookmarksButton"), By.text("Bookmarks"))
    assertTrue("bookmarks sheet should offer a create action", elementExists(By.text("Create bookmark")))
  }

  /**
   * Reads the rows and taps one in a single pass: the list recomposes while playback settles,
   * and a row read a moment ago may be gone by the time it is tapped. Returns the number of
   * the tapped row, or null when every row is the current chapter.
   */
  private fun UiAutomatorTestScope.tapChapterRowOtherThan(current: Int): Int? {
    val deadline = System.currentTimeMillis() + 15_000
    while (true) {
      try {
        val row =
          device
            .findObjects(By.text(ROW))
            .firstOrNull { leadingNumber(it.text.toString()) != current }
            ?: return null
        val target = leadingNumber(row.text.toString())
        row.click()
        return target
      } catch (ex: StaleObjectException) {
        if (System.currentTimeMillis() >= deadline) throw ex
        Thread.sleep(300)
      }
    }
  }

  private fun UiAutomatorTestScope.chapterNumber(): Int = parseChapterNumber(textOf(By.res("playerChapterNumber"), 10_000))

  private fun UiAutomatorTestScope.awaitChapterNumber(from: Int): Int {
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
      val n = runCatching { textOf(By.res("playerChapterNumber"), 2_000) }.getOrNull()?.let { parseChapterNumber(it) }
      if (n != null && n != -1 && n != from) return n
      Thread.sleep(300)
    }
    return chapterNumber()
  }

  private fun parseChapterNumber(text: String): Int = Regex("Chapter (\\d+) of").find(text)?.groupValues?.get(1)?.toInt() ?: -1

  private fun UiAutomatorTestScope.speedCurrent(): String = textOf(By.text(Pattern.compile("^\\d\\.\\d\\dx$")), 10_000)

  private fun leadingNumber(text: String): Int =
    Regex("^(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: -1

  companion object {
    val ROW = Pattern.compile("^\\d+\\s-\\s.*")
  }
}
