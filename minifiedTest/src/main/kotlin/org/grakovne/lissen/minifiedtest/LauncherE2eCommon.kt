package org.grakovne.lissen.minifiedtest

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.UiObject2

const val LAUNCHER_WAIT_MS = 10_000L
const val WIDGET_WAIT_MS = 30_000L

const val STATE_WIDGET_LABEL = "Now playing"
const val COVER_WIDGET_LABEL = "Now playing (cover)"

// The swipe must be fast or the gesture system treats it as a home swipe
fun UiAutomatorTestScope.openAppDrawer() {
  val allApps = By.clazz("android.widget.GridView")
  repeat(3) {
    device.executeShellCommand(
      "input swipe ${device.displayWidth / 2} ${device.displayHeight - 100} " +
        "${device.displayWidth / 2} ${device.displayHeight / 3} 200",
    )
    Thread.sleep(1_500)
    if (device.findObject(allApps) != null) return
  }
}

// Injected as a zero-distance swipe because launcher icons ignore
// accessibility long-click actions
fun UiAutomatorTestScope.longPressOn(target: UiObject2) {
  val b = target.visibleBounds
  device.executeShellCommand("input swipe ${b.centerX()} ${b.centerY()} ${b.centerX()} ${b.centerY()} 800")
  Thread.sleep(800)
}

// Opens the widget picker on the app's section via the long-press popup of
// the app icon
fun UiAutomatorTestScope.openWidgetPicker() {
  device.pressHome()
  Thread.sleep(1_000)
  openAppDrawer()
  longPressOn(scrollUntilVisible(By.text("Lissen"), 12))
  clickElement(By.text("Widgets"), LAUNCHER_WAIT_MS)
  waitForElement(By.text("Lissen"), LAUNCHER_WAIT_MS)
}

// Pins the widget by dragging its preview from the picker onto the workspace.
// An already pinned instance is reused and false is returned: the widget cannot
// be removed reliably with synthetic gestures (its clickable content consumes
// the long-press) and APPWIDGET_UPDATE is a protected broadcast, so the suite
// treats one instance per widget type as the ambient device state.
fun UiAutomatorTestScope.pinWidget(widgetLabel: String): Boolean {
  device.pressHome()
  if (device.findObject(By.desc(widgetLabel)) != null) return false
  openWidgetPicker()
  // The picker list keeps settling after it opens; a drag started too early
  // is swallowed as a scroll
  Thread.sleep(1_500)
  repeat(3) {
    if (device.findObject(By.text(widgetLabel)) == null) {
      if (device.findObject(By.desc(widgetLabel)) != null) {
        Thread.sleep(1_000)
        return true
      }
      openWidgetPicker()
      Thread.sleep(1_500)
    }
    val caption = scrollUntilVisible(By.text(widgetLabel), 8)
    val captionBounds = caption.visibleBounds
    val cell =
      device
        .findObjects(By.clazz("com.android.launcher3.widget.WidgetCell"))
        .firstOrNull { it.visibleBounds.contains(captionBounds.centerX(), captionBounds.top - 1) }
        ?: error("widget cell for '$widgetLabel' not found")
    val cellBounds = cell.visibleBounds
    val startX = cellBounds.centerX()
    val startY = (cellBounds.top + captionBounds.top) / 2
    // Dropping in the upper half of the workspace is rejected as an invalid
    // target on the CI launcher; the lower half always has room
    val dropX = device.displayWidth / 2
    val dropY = device.displayHeight * 13 / 24
    device.executeShellCommand("input draganddrop $startX $startY $dropX $dropY 2000")
    Thread.sleep(2_000)
    // When the drop point overlaps a pinned widget the launcher asks which
    // page to move things to; the highlighted page is fine
    device.findObject(By.text("Move here"))?.click()
    // A resizable widget lands in resize mode, where the launcher exposes an
    // empty accessibility tree; going home commits the placement
    device.pressHome()
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline) {
      if (device.findObject(By.desc(widgetLabel)) != null) {
        Thread.sleep(1_000)
        return true
      }
      Thread.sleep(300)
    }
  }
  throw AssertionError("widget '$widgetLabel' was not pinned after 3 drag attempts")
}

fun UiAutomatorTestScope.widgetHost(label: String): UiObject2 = waitForElement(By.desc(label), WIDGET_WAIT_MS)

fun UiAutomatorTestScope.awaitMediaSessionState(
  expected: String,
  timeoutMs: Long = WIDGET_WAIT_MS,
) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (mediaSessionState() == expected) return
    Thread.sleep(500)
  }
  throw AssertionError("media session never reached $expected, last state: ${mediaSessionState()}")
}

fun UiAutomatorTestScope.shortcutDump(): String =
  device.executeShellCommand("dumpsys shortcut $TARGET_PACKAGE")

fun UiAutomatorTestScope.awaitShortcutRegistered(
  shortcutId: String,
  timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (shortcutDump().contains(shortcutId)) return
    Thread.sleep(500)
  }
  throw AssertionError("dynamic shortcut $shortcutId missing from shortcuts dump")
}
