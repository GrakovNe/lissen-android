package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertFalse
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
}
