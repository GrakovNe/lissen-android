package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class LibraryFlowE2ETest {
  @Test
  fun library_showsBookGridAfterLogin() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    assertFalse(
      "library grid should contain book items",
      device.findObjects(By.res(Pattern.compile("bookItem_.*"))).isEmpty(),
    )
  }

  @Test
  fun library_searchFiltersBooksAndClearRestores() = loggedInApp {
    waitForElement(By.res("libraryGrid"))
    val query = firstBookTitle().take(6)
    clickElement(By.desc("Search"))
    waitForElement(By.res("librarySearchField")).setText(query)
    waitForElement(By.res(Pattern.compile("bookItem_.*")))
    val results = device.findObjects(By.res(Pattern.compile("bookItem_.*")))
    assertTrue("search for '$query' should return results", results.isNotEmpty())
    clickElement(By.desc("Clear"))
    waitForElement(By.res(Pattern.compile("bookItem_.*")))
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
    assertFalse(device.findObjects(By.res(Pattern.compile("bookItem_.*"))).isEmpty())
  }

  private fun UiAutomatorTestScope.firstBookTitle(): String {
    val item = device.findObjects(By.res(Pattern.compile("bookItem_.*"))).first()
    val textNodes = item.findObjects(By.text(Pattern.compile(".+")))
    return textNodes.first().text.toString()
  }
}
