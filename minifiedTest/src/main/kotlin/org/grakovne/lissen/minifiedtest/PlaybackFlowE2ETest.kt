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
class PlaybackFlowE2ETest {
  @Test
  fun playback_playStartsAndPauseStops() = loggedInApp {
    openFirstBookAndReady()
    clickElement(By.desc("Play"))
    waitForElement(By.desc("Pause"))
    assertEquals("media session should report PLAYING", "PLAYING(3)", mediaSessionState())
    clickElement(By.desc("Pause"))
    waitForElement(By.desc("Play"))
  }

  @Test
  fun playback_seekFastForwardAndRewindMovePosition() = loggedInApp {
    openFirstBookAndReady()
    clickElement(By.desc("Play"))
    waitForElement(By.desc("Pause"))
    val before = mediaSessionPositionMs()
    clickElement(By.desc("Fast forward 30 seconds"))
    Thread.sleep(1_500)
    val afterForward = mediaSessionPositionMs()
    assertTrue(
      "fast forward should advance ~30s, got ${afterForward - before}ms",
      afterForward - before in 25_000..35_000,
    )
    clickElement(By.desc("Rewind 10 seconds"))
    Thread.sleep(1_500)
    val afterRewind = mediaSessionPositionMs()
    assertTrue(
      "rewind should go back ~10s, got ${afterForward - afterRewind}ms",
      afterForward - afterRewind in 5_000..15_000,
    )
  }

  @Test
  fun playback_nextAndPreviousTrackChangeChapter() = loggedInApp {
    openFirstBookAndReady()
    clickElement(By.desc("Play"))
    waitForElement(By.desc("Pause"))
    // Freeze the clock before stepping chapters. If playback kept running, the position
    // inside the chapter would cross the replay threshold again while we wait, and a
    // second "Previous track" press would restart the chapter once more instead of
    // stepping back. While paused the position stays exactly where the press left it.
    clickElement(By.desc("Pause"))
    waitForElement(By.desc("Play"))
    val current = currentChapter()
    if (current.number < current.total) {
      clickElement(By.desc("Next track"))
      assertEquals(current.number + 1, awaitChapterNumber(current.number))
      assertEquals(current.number, gotoPreviousChapter(current.number + 1))
    } else {
      assertEquals(current.number - 1, gotoPreviousChapter(current.number))
      clickElement(By.desc("Next track"))
      assertEquals(current.number, awaitChapterNumber(current.number - 1))
    }
  }

  private fun UiAutomatorTestScope.openFirstBookAndReady() {
    waitForElement(By.res(Pattern.compile("bookItem_.*")), 60_000).click()
    waitForElement(By.res("playerScreen"))
    waitForElement(By.res("chapterList"), PLAYBACK_TIMEOUT_MS)
  }

  private fun UiAutomatorTestScope.currentChapter(): ChapterNumber {
    val text = waitForElement(By.res("playerChapterNumber"), 10_000).text.toString()
    val match = Regex("Chapter (\\d+) of (\\d+)").find(text)
    return ChapterNumber(
      match?.groupValues?.get(1)?.toInt() ?: -1,
      match?.groupValues?.get(2)?.toInt() ?: -1,
    )
  }

  // Playback resumes wherever the server left the account. While the position inside the
  // chapter is past the replay threshold (MediaRepository.CURRENT_TRACK_REPLAY_THRESHOLD),
  // "Previous track" restarts the current chapter instead of stepping back, so the first
  // press may leave the chapter number untouched. Press again when that happens: the
  // restart left the position at the chapter start, so the second press steps back.
  private fun UiAutomatorTestScope.gotoPreviousChapter(from: Int): Int {
    clickElement(By.desc("Previous track"))
    val pressed = awaitChapterNumber(from)
    if (pressed != from) return pressed
    clickElement(By.desc("Previous track"))
    return awaitChapterNumber(from)
  }

  private fun UiAutomatorTestScope.awaitChapterNumber(from: Int): Int {
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
      val n = currentChapter().number
      if (n != from) return n
      Thread.sleep(300)
    }
    return currentChapter().number
  }

  private data class ChapterNumber(val number: Int, val total: Int)

  companion object {
    const val PLAYBACK_TIMEOUT_MS = 120_000L
  }
}
