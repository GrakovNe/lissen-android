package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class LibraryFlowE2ETest {
  @Test
  fun library_showsBookGridAfterLogin() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    // the grid container renders before the first page of books arrives from the server
    waitForElement(By.res(Pattern.compile("bookItem_.*")), 60_000)
  }

  @Test
  fun library_searchFiltersBooksAndBackRestores() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    val query = firstBookTitle().take(6)
    clickElement(By.desc("Search"))
    waitForElement(By.res("librarySearchField")).setText(query)
    waitForElement(By.res(Pattern.compile("bookItem_.*")))
    val results = device.findObjects(By.res(Pattern.compile("bookItem_.*")))
    assertTrue("search for '$query' should return results", results.isNotEmpty())
    clickElement(By.desc("Clear"))
    // the clear button empties the field but keeps the user in search mode, and a blank
    // query deliberately has no results, so the grid goes empty rather than showing everything
    waitUntilAbsent(By.res(Pattern.compile("bookItem_.*")), 15_000)
    clickElement(By.desc("Back"))
    // leaving search re-fetches the whole library; on a slow link this can exceed the
    // default timeout, so give the restore a wider budget
    waitForElement(By.res(Pattern.compile("bookItem_.*")), 90_000)
  }

  @Test
  fun library_groupingBySeries_appliedAndReverted() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    clickElement(By.desc("Menu"))
    waitForElement(By.text("Grouping"))
    clickElement(By.text("Grouping"))
    clickElement(By.text("By Series"))
    pressBack()
    clickElement(By.desc("Menu"))
    waitForElement(By.text("Grouping"))
    waitForElement(By.text("By Series"))
    pressBack()
    clickElement(By.desc("Menu"))
    waitForElement(By.text("Grouping"))
    clickElement(By.text("Grouping"))
    clickElement(By.text("Disabled"))
    pressBack()
    waitForElement(By.res("libraryGrid"))
  }

  @Test
  fun library_quickSettingsToggles_downloadedOnlyFiltersAndRestores() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    clickElement(By.desc("Menu"))
    waitForElement(By.text("Downloaded only"))
    waitForElement(By.text("Hide finished"))
    clickElement(By.text("Downloaded only"))
    pressBack()
    waitForElement(By.text("No saved books yet"))
    clickElement(By.desc("Menu"))
    waitForElement(By.text("Downloaded only"))
    clickElement(By.text("Downloaded only"))
    pressBack()
    waitForElement(By.res("libraryGrid"))
    waitForElement(By.res(Pattern.compile("bookItem_.*")), 90_000)
  }

  @Test
  fun library_openingBook_showsPlayer() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    waitForElement(By.res(Pattern.compile("bookItem_.*")), 60_000).click()
    waitForElement(By.res("playerScreen"))
  }

  private fun UiAutomatorTestScope.firstBookTitle(): String {
    val item = waitForElement(By.res(Pattern.compile("bookItem_.*")), 60_000)
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline) {
      val title = item.findObjects(By.text(Pattern.compile(".+"))).firstOrNull()?.text?.toString()
      if (!title.isNullOrEmpty()) return title
      Thread.sleep(300)
    }
    throw AssertionError("the first book item has no title text")
  }
}
