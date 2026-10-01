package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class LibraryGapsE2ETest {
  @Test
  fun library_searchWithoutResults_showsEmptyGridAndRestores() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    waitForElement(BOOK_ITEM, 60_000)
    clickElement(By.desc("Search"))
    setTextOf(By.res("librarySearchField"), "zzqx${UUID.randomUUID().toString().take(6)}vv")
    waitUntilAbsent(BOOK_ITEM, 30_000)
    clickElement(By.desc("Clear"))
    clickElement(By.desc("Back"))
    waitForElement(BOOK_ITEM, 90_000)
  }

  @Test
  fun library_searchWithSpecialCharacters_doesNotCrash() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    waitForElement(BOOK_ITEM, 60_000)
    clickElement(By.desc("Search"))
    setTextOf(By.res("librarySearchField"), "«%&\"()\\_")
    Thread.sleep(3_000)
    assertTrue("search field must survive a special-character query", elementExists(By.res("librarySearchField")))
    clickElement(By.desc("Clear"))
    clickElement(By.desc("Back"))
    waitForElement(By.res("libraryGrid"))
    assertAppAlive()
  }

  @Test
  fun library_sortByAuthor_appliedPersistsAndReverts() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    openQuickSettings(By.text("Sort by"))
    clickElement(By.text("Sort by"))
    clickElement(By.text("Author"))
    pressBack()
    openQuickSettings(By.text("Sort by"))
    assertTrue("sort row should still show Author after reopening the sheet", elementExists(By.text("Author")))
    clickElement(By.text("Sort by"))
    clickElement(By.text("Title"))
    pressBack()
    waitForElement(BOOK_ITEM, 90_000)
  }

  @Test
  fun library_groupingByAuthor_showsAuthorsAndReverts() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    openQuickSettings(By.text("Grouping"))
    clickElement(By.text("Grouping"))
    clickElement(By.text("By Author"))
    pressBack()
    waitForElement(AUTHOR_ITEM, 60_000)
    openQuickSettings(By.text("Grouping"))
    clickElement(By.text("Grouping"))
    clickElement(By.text("Disabled"))
    pressBack()
    waitForElement(BOOK_ITEM, 90_000)
  }

  @Test
  fun library_hideFinishedToggle_roundTripsWithoutCrash() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    waitForElement(BOOK_ITEM, 60_000)
    openQuickSettings(By.text("Hide finished"))
    clickElement(By.text("Hide finished"))
    pressBack()
    waitForElement(By.res("libraryGrid"))
    openQuickSettings(By.text("Hide finished"))
    clickElement(By.text("Hide finished"))
    pressBack()
    waitForElement(BOOK_ITEM, 90_000)
    assertAppAlive()
  }

  @Test
  fun library_continueListening_showsTheBookJustPlayed() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    openFirstBook()
    clickElement(By.desc("Play"))
    waitForElement(By.desc("Pause"))
    pressBack()
    waitForElement(By.res("libraryScreen"))
    waitForElement(BOOK_ITEM, 60_000)
    assertEquals(
      "the shelf on top of the library should be Continue listening",
      "Continue listening",
      textOf(By.res("libraryNavBarTitle"), 15_000),
    )
  }

  private fun UiAutomatorTestScope.openQuickSettings(expected: BySelector) = clickUntil(By.desc("Menu"), expected)

  private fun UiAutomatorTestScope.assertAppAlive() {
    val pid = device.executeShellCommand("pidof $TARGET_PACKAGE").trim()
    if (pid.isEmpty()) throw AssertionError("$TARGET_PACKAGE crashed")
  }

  private companion object {
    val BOOK_ITEM = By.res(Pattern.compile("bookItem_.*"))
    val AUTHOR_ITEM = By.res(Pattern.compile("authorItem_.*"))
  }
}
