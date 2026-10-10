package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressOnOpenE2ETest {
  @Test
  fun progress_openReportsNothing() {
    clearApp()
    val server = ServerAccount.login()
    val book = server.lastBookByTitle()
    server.finishAt(book.id, FINISHED_AT_SECONDS)
    val before = server.progress(book.id)
    assertTrue("the seed must leave the book finished", before.isFinished)
    assertEquals("the seed must leave the position inside the book", FINISHED_AT_SECONDS, before.currentTime, 0.0)

    loggedInApp {
      openSeededBook(book)
      Thread.sleep(SETTLE_MS)
    }

    val after = server.progress(book.id)
    assertTrue("opening the book must not drop the finished mark", after.isFinished)
    assertEquals("opening the book must not touch the progress at all", before.lastUpdate, after.lastUpdate)
  }

  @Test
  fun progress_playStillReportsThePosition() {
    clearApp()
    val server = ServerAccount.login()
    val book = server.lastBookByTitle()
    server.finishAt(book.id, FINISHED_AT_SECONDS)
    val before = server.progress(book.id)

    loggedInApp {
      openSeededBook(book)
      listenUntilAdvanced()
    }

    val deadline = System.currentTimeMillis() + REPORT_TIMEOUT_MS
    while (true) {
      val after = server.progress(book.id)
      if (after.currentTime > before.currentTime) return
      if (System.currentTimeMillis() >= deadline) throw AssertionError("playing must report a later position, got ${after.currentTime}")
      Thread.sleep(1_000)
    }
  }

  private fun UiAutomatorTestScope.openSeededBook(book: ServerBook) {
    openBook(By.res("bookItem_${book.id}"))
    waitForElement(By.text(book.title))
  }

  /** The session dump holds the position of the last state push, and a pause pushes one. */
  private fun UiAutomatorTestScope.listenUntilAdvanced() {
    val start = mediaSessionPositionMs()
    check(start >= 0) { "no position in the media session dump" }
    val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
    while (true) {
      clickElement(By.desc("Play"))
      waitForElement(By.desc("Pause"))
      Thread.sleep(LISTEN_MS)
      clickElement(By.desc("Pause"))
      waitForElement(By.desc("Play"))
      if (mediaSessionPositionMs() > start) return
      if (System.currentTimeMillis() >= deadline) throw AssertionError("playback did not advance from ${start}ms")
    }
  }

  companion object {
    // sub-millisecond digits as the web player stores them, more than 10 s from the end (#296)
    const val FINISHED_AT_SECONDS = 40.123456
    const val LISTEN_MS = 2_000L
    const val SETTLE_MS = 8_000L
    const val REPORT_TIMEOUT_MS = 8_000L
  }
}
