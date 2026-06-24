package org.grakovne.lissen.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

const val TARGET_PACKAGE = "org.grakovne.lissen.benchmark"

private val BOOK_ITEM = Pattern.compile("bookItem_.*")
private const val TIMEOUT = 15_000L

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule
  val rule = BaselineProfileRule()

  @Test
  fun generate() =
    rule.collect(
      packageName = TARGET_PACKAGE,
      includeInStartupProfile = true,
    ) {
      pressHome()
      startActivityAndWait()
      loginIfNeeded()

      runCatching { scrollLibrary() }
      runCatching { searchFlow() }
      runCatching { settingsFlow() }

      playerFlow()
    }

  private fun MacrobenchmarkScope.loginIfNeeded() {
    val host = device.wait(Until.findObject(By.res("hostInput")), 10_000) ?: return

    val args = InstrumentationRegistry.getArguments()
    val url = requireNotNull(args.getString("serverUrl")) { "missing -P serverUrl arg" }
    val user = requireNotNull(args.getString("username")) { "missing -P username arg" }
    val pass = requireNotNull(args.getString("password")) { "missing -P password arg" }

    host.text = url
    device.findObject(By.res("usernameInput")).text = user
    device.findObject(By.res("passwordInput")).text = pass
    device.findObject(By.res("loginButton")).click()

    device.wait(Until.hasObject(By.res(BOOK_ITEM)), 30_000)
    dismissNotificationDialog()
  }

  private fun MacrobenchmarkScope.dismissNotificationDialog() {
    device
      .wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button")), 3_000)
      ?.click()
  }

  private fun MacrobenchmarkScope.scrollLibrary() {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(3) {
      device.swipe(w / 2, (h * 0.7).toInt(), w / 2, (h * 0.3).toInt(), 8)
      device.waitForIdle()
    }
    device.swipe(w / 2, (h * 0.3).toInt(), w / 2, (h * 0.8).toInt(), 8)
    device.wait(Until.hasObject(By.res(BOOK_ITEM)), TIMEOUT)
  }

  private fun MacrobenchmarkScope.searchFlow() {
    device.findObject(By.desc("Search"))?.click() ?: return
    val field = device.wait(Until.findObject(By.res("librarySearchField")), 5_000) ?: return
    field.text = "а"
    device.wait(Until.hasObject(By.res(BOOK_ITEM)), TIMEOUT)
    device.waitForIdle()
    device.pressBack()
    device.pressBack()
    device.wait(Until.hasObject(By.res(BOOK_ITEM)), TIMEOUT)
  }

  private fun MacrobenchmarkScope.settingsFlow() {
    device.findObject(By.desc("Menu"))?.click() ?: return
    device.wait(Until.hasObject(By.res("librarySettingsSheet")), 5_000)
    device.waitForIdle()

    val settingsItem = device.wait(Until.findObject(By.res("appSettingsItem")), 3_000)
    if (settingsItem == null) {
      device.pressBack()
      return
    }

    settingsItem.click()
    device.wait(Until.hasObject(By.res("settingsScreen")), TIMEOUT)
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.hasObject(By.res(BOOK_ITEM)), TIMEOUT)
  }

  private fun MacrobenchmarkScope.playerFlow() {
    repeat(2) {
      val book = device.wait(Until.findObject(By.res(BOOK_ITEM)), TIMEOUT) ?: return
      book.click()
      device.wait(Until.hasObject(By.res("playerScreen")), TIMEOUT)
      device.waitForIdle()

      device.findObject(By.res("chapterList"))?.let { list ->
        runCatching {
          list.fling(Direction.DOWN)
          device.waitForIdle()
          list.fling(Direction.UP)
        }
      }

      device.pressBack()
      device.wait(Until.hasObject(By.res(BOOK_ITEM)), TIMEOUT)
    }
  }
}
