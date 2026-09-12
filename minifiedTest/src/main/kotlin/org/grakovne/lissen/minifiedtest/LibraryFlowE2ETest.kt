package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.UiObject2
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

  private fun UiAutomatorTestScope.firstBookTitle(): String {
    val item = device.findObjects(By.res(Pattern.compile("bookItem_.*"))).first()
    val textNodes = item.findObjects(By.text(Pattern.compile(".+")))
    return textNodes.first().text.toString()
  }
}
