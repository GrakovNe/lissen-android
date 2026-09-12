package org.grakovne.lissen.minifiedtest

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.UiObject2

const val DEFAULT_TIMEOUT_MS = 45_000L
const val SHORT_MS = 5_000L

fun UiAutomatorTestScope.waitForElement(
  selector: BySelector,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
): UiObject2 {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    device.findObject(selector)?.let { return it }
    Thread.sleep(300)
  }
  throw AssertionError("No element matching $selector within ${timeoutMs}ms")
}

fun UiAutomatorTestScope.clickElement(
  selector: BySelector,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
): UiObject2 = waitForElement(selector, timeoutMs).also { it.click() }

fun UiAutomatorTestScope.elementExists(
  selector: BySelector,
  timeoutMs: Long = SHORT_MS,
): Boolean {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (device.findObject(selector) != null) return true
    Thread.sleep(300)
  }
  return false
}

fun UiAutomatorTestScope.scrollUntilVisible(
  selector: BySelector,
  maxScrolls: Int = 8,
): UiObject2 {
  repeat(maxScrolls) {
    device.findObject(selector)?.let { return it }
    device.swipe(
      device.displayWidth / 2,
      device.displayHeight * 3 / 4,
      device.displayWidth / 2,
      device.displayHeight / 4,
      10,
    )
  }
  return waitForElement(selector)
}

const val TARGET_PACKAGE = "org.grakovne.lissen.minified"

fun e2eArgument(name: String, fallback: String): String =
  androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

fun freshApp(block: UiAutomatorTestScope.() -> Unit) =
  androidx.test.uiautomator.uiAutomator {
    androidx.test.shell.Shell.application.clearAppData(TARGET_PACKAGE)
    watchFor(androidx.test.uiautomator.watcher.PermissionDialog) { clickAllow() }
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    block()
  }

fun UiAutomatorTestScope.loginToLibrary(password: String = e2eArgument("e2ePassword", "demo")) {
  waitForElement(By.res("hostInput"))
    .setText(e2eArgument("e2eHost", "https://demo.lissenapp.org"))
  waitForElement(By.res("usernameInput")).setText(e2eArgument("e2eUsername", "demo"))
  waitForElement(By.res("passwordInput")).setText(password)
  clickElement(By.res("loginButton"))
  waitForElement(By.res("libraryScreen"))
}

fun loggedInApp(block: UiAutomatorTestScope.() -> Unit) = freshApp {
  loginToLibrary()
  block()
}
