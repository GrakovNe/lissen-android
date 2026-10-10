package org.grakovne.lissen.minifiedtest

import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
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

/**
 * A [UiObject2] is a snapshot of one accessibility node; Compose replaces nodes on every
 * recomposition, so anything done to an object found a moment ago can hit a node that no
 * longer exists ([StaleObjectException]). Every interaction therefore looks the element up
 * again and retries while the node keeps changing under it, until [timeoutMs] runs out.
 */
fun <T> UiAutomatorTestScope.onFreshElement(
  selector: BySelector,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
  action: (UiObject2) -> T,
): T {
  val deadline = System.currentTimeMillis() + timeoutMs
  var stale: StaleObjectException? = null
  while (System.currentTimeMillis() < deadline) {
    val element = device.findObject(selector)
    if (element == null) {
      Thread.sleep(300)
      continue
    }
    try {
      return action(element)
    } catch (ex: StaleObjectException) {
      stale = ex
      Thread.sleep(300)
    }
  }
  throw AssertionError(
    when (stale) {
      null -> "No element matching $selector within ${timeoutMs}ms"
      else -> "Element matching $selector kept changing for ${timeoutMs}ms"
    },
    stale,
  )
}

fun UiAutomatorTestScope.clickElement(
  selector: BySelector,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
  onFreshElement(selector, timeoutMs) { it.click() }
}

fun UiAutomatorTestScope.textOf(
  selector: BySelector,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
): String = onFreshElement(selector, timeoutMs) { it.text.toString() }

fun UiAutomatorTestScope.setTextOf(
  selector: BySelector,
  text: String,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
  onFreshElement(selector, timeoutMs) { it.setText(text) }
}

/**
 * Taps [trigger] until [expected] shows up. A tap that lands while the screen is still
 * settling (a navigation transition, the first frame of a freshly loaded list) is silently
 * lost on the CI emulator; waiting for the outcome and tapping again is what a person would
 * do. [settleMs] is longer than any sheet or dialog animation, so a second tap can only
 * follow a tap that did nothing.
 *
 * A tap is sometimes not lost but merely late: the screen is busy loading and acts on it
 * after [settleMs] has passed. [expected] is then already up and covers [trigger], so every
 * lookup for the next tap also watches for [expected] and takes it as the outcome.
 */
fun UiAutomatorTestScope.clickUntil(
  trigger: BySelector,
  expected: BySelector,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
  settleMs: Long = 8_000L,
) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (true) {
    if (tapTriggerUnlessExpected(trigger, expected, timeoutMs)) return
    if (elementExists(expected, settleMs)) return
    if (System.currentTimeMillis() >= deadline) {
      throw AssertionError("Tapping $trigger never brought up $expected within ${timeoutMs}ms")
    }
    Log.w(E2E_TAG, "tap on $trigger did not bring up $expected, tapping again")
  }
}

/** Taps [trigger]; returns true when [expected] turned up before a tap could be made. */
private fun UiAutomatorTestScope.tapTriggerUnlessExpected(
  trigger: BySelector,
  expected: BySelector,
  timeoutMs: Long,
): Boolean {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (device.findObject(expected) != null) return true
    val element = device.findObject(trigger)
    if (element == null) {
      Thread.sleep(300)
      continue
    }
    try {
      element.click()
      return false
    } catch (_: StaleObjectException) {
      Thread.sleep(300)
    }
  }
  throw AssertionError("No element matching $trigger within ${timeoutMs}ms")
}

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

private fun e2eArgument(name: String, fallback: String): String =
  androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(name) ?: fallback

val E2E_HOST: String
  get() = e2eArgument("e2eHost", "https://demo.lissenapp.org")

val E2E_USERNAME: String
  get() = e2eArgument("e2eUsername", "demo")

val E2E_PASSWORD: String
  get() = e2eArgument("e2ePassword", "demo")

const val LOGIN_SCREEN_WAIT_MS = 15_000L

/** Kills the instance a previous test left behind with its data; a paused one may still report once. */
fun clearApp() {
  // the uiautomator shell server occasionally fails to start on a busy emulator
  repeat(3) {
    try {
      androidx.test.shell.Shell.application.clearAppData(TARGET_PACKAGE)
      return
    } catch (_: IllegalStateException) {
      Thread.sleep(2_000)
    }
  }
  androidx.test.shell.Shell.application.clearAppData(TARGET_PACKAGE)
}

fun freshApp(block: UiAutomatorTestScope.() -> Unit) =
  androidx.test.uiautomator.uiAutomator {
    // the launcher ANRs on the CI emulator often enough that its system dialog covers the
    // app window and every selector lookup fails behind it
    device.executeShellCommand("settings put global hide_error_dialogs 1")
    clearApp()
    waitForAppGone()
    watchFor(androidx.test.uiautomator.watcher.PermissionDialog) { clickAllow() }
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    ensureLoginScreen()
    block()
  }

// Starting the app while the framework is still removing the task of the cleared instance
// makes it kill the freshly started process, and on the slow CI emulator that race repeats
// until the launch machinery wedges; wait until both the process and its activity records
// are gone before launching again.
//
// The display keeps naming the removed task as mLastFocusedRootTask until another task takes
// focus, which never happens between two tests; that line is not a record of the app and is
// left out, otherwise every reset sits through the whole timeout. The framework kills the
// processes of a removed task one second after removing it, so the settle pause has to
// outlast that.
private fun UiAutomatorTestScope.waitForAppGone(timeoutMs: Long = 15_000) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    val pid = device.executeShellCommand("pidof $TARGET_PACKAGE").trim()
    val records =
      device
        .executeShellCommand("dumpsys activity activities")
        .lineSequence()
        .any { TARGET_PACKAGE in it && "mLastFocusedRootTask" !in it }
    if (pid.isEmpty() && !records) {
      Thread.sleep(3_000)
      return
    }
    Thread.sleep(250)
  }
  Log.w(E2E_TAG, "$TARGET_PACKAGE is still present ${timeoutMs}ms after clearing its data")
}

// The app renders its first frame behind a system dialog on the CI emulator often enough
// to fail the whole suite; record what the device was showing at that moment, get rid of
// the dialog and launch again instead of letting every test time out on the login screen.
fun UiAutomatorTestScope.ensureLoginScreen() {
  if (elementExists(By.res("hostInput"), LOGIN_SCREEN_WAIT_MS)) return
  Log.w(E2E_TAG, "$TARGET_PACKAGE is in the foreground but the login screen is missing")
  dumpWindowFocus()
  dumpProcesses()
  dumpScreen("e2e-missing-login")
  dismissSystemDialog()
  if (elementExists(By.res("hostInput"), LOGIN_SCREEN_WAIT_MS)) return
  device.executeShellCommand("am force-stop $TARGET_PACKAGE")
  startApp(TARGET_PACKAGE)
  waitForAppToBeVisible(TARGET_PACKAGE)
}

private fun UiAutomatorTestScope.dismissSystemDialog() {
  device.executeShellCommand("settings put global hide_error_dialogs 1")
  for (label in listOf("Wait", "Close app", "OK")) {
    try {
      device.findObject(By.text(label))?.click()
    } catch (_: StaleObjectException) {
      // The dialog vanished on its own, which is exactly the state we want.
    }
  }
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

const val LOGIN_TIMEOUT_MS = 120_000L
const val LOGIN_ATTEMPT_MS = 30_000L

/**
 * A login round trip takes a second; an attempt that has not reached the library in
 * [LOGIN_ATTEMPT_MS] has lost its tap or its input on the way (the emulator drops both while
 * the keyboard or the screen is still settling), so the form is filled in and submitted
 * again. A failed attempt only shows a toast and leaves the form in place, so a second
 * submit is always safe; what the device showed at that moment is recorded for the CI logs.
 */
fun UiAutomatorTestScope.loginToLibrary(password: String = E2E_PASSWORD) {
  val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
  var attempt = 0
  while (true) {
    attempt++
    setTextOf(By.res("hostInput"), E2E_HOST)
    setTextOf(By.res("usernameInput"), E2E_USERNAME)
    setTextOf(By.res("passwordInput"), password)
    clickElement(By.res("loginButton"))
    if (elementExists(By.res("libraryScreen"), LOGIN_ATTEMPT_MS)) return
    if (System.currentTimeMillis() >= deadline) {
      throw AssertionError("login did not reach the library in $attempt attempts within ${LOGIN_TIMEOUT_MS}ms")
    }
    Log.w(E2E_TAG, "login attempt $attempt did not reach the library, submitting again")
    dumpScreen("e2e-login-attempt-$attempt")
    // the form is the only place the button exists; without it the app is on its way somewhere
    if (elementExists(By.res("loginButton"), 2_000).not() && elementExists(By.res("libraryScreen"), LOGIN_ATTEMPT_MS)) return
  }
}

fun loggedInApp(block: UiAutomatorTestScope.() -> Unit) = freshApp {
  loginToLibrary()
  block()
}

val anyBook: BySelector = By.res(java.util.regex.Pattern.compile("bookItem_.*"))

/** A swipe is a fling: the grid keeps moving after the item is first seen, and a tap lands where it was. */
private fun UiAutomatorTestScope.awaitSettled(selector: BySelector) {
  val boundsOf = { runCatching { device.findObject(selector)?.visibleBounds }.getOrNull() }
  var bounds = boundsOf()
  repeat(20) {
    Thread.sleep(300)
    val now = boundsOf()
    if (now != null && now == bounds) return
    bounds = now
  }
}

fun UiAutomatorTestScope.openFirstBook() = openBook(anyBook)

/** The grid fills after login; an empty grid would eat the swipes. */
fun UiAutomatorTestScope.openBook(book: BySelector) {
  waitForElement(anyBook, 60_000)
  scrollUntilVisible(book)
  awaitSettled(book)
  clickUntil(book, By.res("playerScreen"))
  // the player draws a placeholder (same chapter-number tag, same tab labels, none of it
  // interactive) until playback is ready; the track controls are the first thing that only
  // the ready player has. The chapter list is the content of the "Chapters" tab and is not
  // present until that tab is selected
  waitForElement(By.res("trackControls"), 120_000)
}

fun UiAutomatorTestScope.mediaSessionState(): String {
  val dump = device.executeShellCommand("dumpsys media_session")
  return Regex("state=PlaybackState \\{state=([A-Z]+\\(\\d+\\))").find(dump)?.groupValues?.get(1) ?: ""
}

fun UiAutomatorTestScope.mediaSessionPositionMs(): Long {
  val dump = device.executeShellCommand("dumpsys media_session")
  return Regex("position=(\\d+), buffered").find(dump)?.groupValues?.get(1)?.toLong() ?: -1L
}

fun UiAutomatorTestScope.assertAppAlive(
  context: String = "",
) {
  val pid = device.executeShellCommand("pidof $TARGET_PACKAGE").trim()
  if (pid.isEmpty()) throw AssertionError("$TARGET_PACKAGE crashed$context")
}

/**
 * Taps the icon-only (X) button of a dialog preset row: the leftmost button of the row
 * holding [referenceText]. Preset captions live in child TextViews, so the row is located
 * by bounds containment rather than by the buttons' (empty) text.
 */
fun UiAutomatorTestScope.tapButtonLeftOf(referenceText: String) {
  val deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS
  while (System.currentTimeMillis() < deadline) {
    val buttons = device.findObjects(By.clazz("android.widget.Button"))
    val referenceButton =
      buttons.firstOrNull { button ->
        device
          .findObjects(By.text(referenceText))
          .any { it.visibleBounds.contains(button.visibleBounds.centerX(), button.visibleBounds.centerY()) }
      }
    if (referenceButton != null) {
      val leftmost =
        buttons
          .filter { kotlin.math.abs(it.visibleBounds.centerY() - referenceButton.visibleBounds.centerY()) < 40 }
          .minByOrNull { it.visibleBounds.centerX() }
      if (leftmost != null && leftmost != referenceButton) {
        leftmost.click()
        return
      }
    }
    Thread.sleep(300)
  }
  throw AssertionError("no icon-only button found left of '$referenceText'")
}
