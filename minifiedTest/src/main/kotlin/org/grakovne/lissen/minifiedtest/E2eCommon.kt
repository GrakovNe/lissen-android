package org.grakovne.lissen.minifiedtest

import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.UiObject2
import java.io.File

const val DEFAULT_TIMEOUT_MS = 45_000L
const val SHORT_MS = 5_000L
const val E2E_TAG = "LissenE2E"

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

fun UiAutomatorTestScope.waitUntilAbsent(
  selector: BySelector,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (device.findObject(selector) == null) return
    Thread.sleep(300)
  }
  throw AssertionError("Element matching $selector is still present after ${timeoutMs}ms")
}

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

const val LOGIN_SCREEN_WAIT_MS = 15_000L

fun e2eArgument(name: String, fallback: String): String =
  androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

fun freshApp(block: UiAutomatorTestScope.() -> Unit) =
  androidx.test.uiautomator.uiAutomator {
    androidx.test.shell.Shell.application.clearAppData(TARGET_PACKAGE)
    watchFor(androidx.test.uiautomator.watcher.PermissionDialog) { clickAllow() }
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    ensureLoginScreen()
    block()
  }

// The app reaches the foreground without rendering its first frame on the CI emulator
// often enough to fail the whole suite; record what the device was showing at that moment
// and launch again instead of letting every test time out on the login screen.
fun UiAutomatorTestScope.ensureLoginScreen() {
  if (elementExists(By.res("hostInput"), LOGIN_SCREEN_WAIT_MS)) return
  Log.w(E2E_TAG, "$TARGET_PACKAGE is in the foreground but the login screen is missing")
  dumpWindowFocus()
  dumpProcesses()
  dumpScreen("e2e-missing-login")
  device.executeShellCommand("am force-stop $TARGET_PACKAGE")
  startApp(TARGET_PACKAGE)
  waitForAppToBeVisible(TARGET_PACKAGE)
}

private fun UiAutomatorTestScope.dumpWindowFocus() {
  val focus =
    device
      .executeShellCommand("dumpsys window")
      .lines()
      .filter { "mCurrentFocus" in it || "mFocusedApp" in it }
      .joinToString(" | ")
  Log.i(E2E_TAG, "focus: ${focus.ifEmpty { "none" }}")
  val resumed =
    device
      .executeShellCommand("dumpsys activity activities")
      .lines()
      .filter { "ResumedActivity" in it || "topResumedActivity" in it }
      .joinToString(" | ")
  Log.i(E2E_TAG, "resumed: ${resumed.ifEmpty { "none" }}")
}

private fun UiAutomatorTestScope.dumpProcesses() {
  val processes =
    device
      .executeShellCommand("ps -A")
      .lines()
      .filter { "lissen" in it }
      .joinToString(" | ")
  Log.i(E2E_TAG, "processes: ${processes.ifEmpty { "none" }}")
}

fun UiAutomatorTestScope.dumpScreen(name: String) {
  val dir =
    androidx.test.platform.app.InstrumentationRegistry
      .getInstrumentation()
      .context
      .getExternalFilesDir(null)
      ?: return
  val hierarchy = File(dir, "$name.xml")
  runCatching {
    device.takeScreenshot(File(dir, "$name.png"))
    device.dumpWindowHierarchy(hierarchy)
  }
  Log.i(E2E_TAG, "screen recorded in ${dir.path}/$name.{png,xml}")
  runCatching { Log.i(E2E_TAG, "hierarchy: ${hierarchy.readText().take(3_000)}") }
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

fun UiAutomatorTestScope.openFirstBook() {
  waitForElement(By.res(java.util.regex.Pattern.compile("bookItem_.*")), 60_000).click()
  waitForElement(By.res("playerScreen"))
  // the player is interactive once the chapter number renders; the chapter list is the
  // content of the "Chapters" tab and is not present until that tab is selected
  waitForElement(By.res("playerChapterNumber"), 120_000)
}

fun UiAutomatorTestScope.mediaSessionState(): String {
  val dump = device.executeShellCommand("dumpsys media_session")
  return Regex("state=PlaybackState \\{state=([A-Z]+\\(\\d+\\))").find(dump)?.groupValues?.get(1) ?: ""
}

fun UiAutomatorTestScope.mediaSessionPositionMs(): Long {
  val dump = device.executeShellCommand("dumpsys media_session")
  return Regex("position=(\\d+), buffered").find(dump)?.groupValues?.get(1)?.toLong() ?: -1L
}
