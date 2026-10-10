package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class PlayerGapsE2ETest {
  @Test
  fun playback_survivesGoingToBackground() = loggedInApp {
    openFirstBook()
    clickElement(By.desc("Play"))
    waitForElement(By.desc("Pause"))
    device.pressHome()
    Thread.sleep(3_000)
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    assertEquals("playback must continue while the app is in background", "PLAYING(3)", mediaSessionState())
  }

  @Test
  fun playback_restoresSameChapterAfterProcessKill() = loggedInApp {
    openFirstBook()
    clickElement(By.desc("Play"))
    waitForElement(By.desc("Pause"))
    val before = chapterNumber()
    assertTrue("a real chapter number is expected before the kill", before > 0)

    device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    Thread.sleep(2_000)
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)

    if (elementExists(By.res("miniPlayer"), 30_000)) clickElement(By.res("miniPlayer"))
    waitForElement(By.res("playerScreen"), 60_000)
    waitForElement(By.res("trackControls"), PLAYBACK_TIMEOUT_MS)
    assertEquals("the chapter playing before the kill should be restored", before, chapterNumber())
  }

  @Test
  fun miniPlayer_showsPlayingChapterUnderTheBookTitle() = loggedInApp {
    val gridItem = waitForElement(By.res(Pattern.compile("bookItem_.*")), 60_000)
    val gridTexts = gridItem.findObjects(By.text(Pattern.compile(".+"))).map { it.text.toString() }
    val bookTitle = gridTexts.firstOrNull().orEmpty()
    val bookAuthor = gridTexts.getOrNull(1)

    openFirstBook()
    clickElement(By.desc("Play"))
    waitForElement(By.desc("Pause"))

    val chapterTitle = playingMediaTitle()
    assertTrue("the playback notification should expose the title of the playing chapter", chapterTitle.isNotBlank())

    pressBack()
    val miniBounds = waitForElement(By.res("miniPlayer"), 30_000).visibleBounds
    val lines = textNodesWithin(miniBounds)
    assertTrue(
      "the mini player should show the playing chapter '$chapterTitle', got $lines",
      lines.any { it.contains(chapterTitle) },
    )
    if (bookTitle.isNotEmpty()) {
      assertTrue("the mini player should keep showing the book title '$bookTitle', got $lines", lines.any { it.contains(bookTitle) })
    }
    if (bookAuthor != null && bookAuthor != bookTitle) {
      assertTrue("the mini player should no longer show the author '$bookAuthor', got $lines", lines.none { it == bookAuthor })
    }
  }

  @Test
  fun player_sleepTimer_setThenCancelled() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Timer"), By.text("Sleep Timer"))
    clickElement(By.text("15"))
    assertTrue("the dialog should reflect the 15 minute selection", elementExists(By.text("15 minutes")))

    tapButtonLeftOf("15")
    assertTrue("the dialog should fall back to Disabled", elementExists(By.text("Disabled")))
    pressBack()

    // the tab label counts down while a timer runs; the plain "Timer" label is back only
    // once the cancellation took effect
    assertTrue("the tab label must return to Timer", elementExists(By.text("Timer"), 15_000))
    clickUntil(By.text("Timer"), By.text("Sleep Timer"))
    assertTrue("a reopened dialog must show the cancelled state", elementExists(By.text("Disabled")))
    pressBack()
  }

  @Test
  fun player_infoButton_showsDetailsAndReturns() = loggedInApp {
    openFirstBook()
    clickElement(By.res("playerInfoButton"))
    // detail rows render as "Author: …" style captions
    val sheetUp =
      elementExists(By.textContains("Author:"), 15_000) ||
        elementExists(By.textContains("Duration:"), 5_000) ||
        elementExists(By.textContains("Narrator:"), 5_000)
    assertTrue("the info sheet should show at least one detail row", sheetUp)
    pressBack()
    waitForElement(By.res("playerScreen"))
    assertTrue("play controls must survive the info round trip", elementExists(By.desc("Play")) || elementExists(By.desc("Pause")))
  }

  @Test
  fun player_speed_raisedAndReturnedToNormal() = loggedInApp {
    openFirstBook()
    clickUntil(By.text("Speed"), By.text("Playback speed"))
    clickElement(By.text("1.5"))
    assertEquals("1.50x", speedCurrent())
    clickElement(By.text("1.0"))
    assertEquals("1.00x", speedCurrent())
    pressBack()
  }

  private fun UiAutomatorTestScope.chapterNumber(): Int =
    Regex("Chapter (\\d+) of").find(textOf(By.res("playerChapterNumber"), 10_000))?.groupValues?.get(1)?.toInt() ?: -1

  private fun UiAutomatorTestScope.speedCurrent(): String =
    textOf(By.text(Pattern.compile("^\\d\\.\\d\\dx$")), 10_000)

  companion object {
    const val PLAYBACK_TIMEOUT_MS = 120_000L
  }
}
