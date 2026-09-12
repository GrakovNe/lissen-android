package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.uiAutomator
import androidx.test.uiautomator.watcher.PermissionDialog
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExploreTmp {
  @Test
  fun explore() = uiAutomator {
    androidx.test.shell.Shell.application.clearAppData(TARGET_PACKAGE)
    watchFor(PermissionDialog) { clickAllow() }
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    loginToLibrary()
    clickElement(By.desc("Menu"))
    Thread.sleep(2500)
    val f = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "sheet.xml")
    device.dumpWindowHierarchy(f)
    println("SWITCH_CLASS=" + device.findObjects(By.clazz("android.widget.Switch")).size)
    println("CHECKABLE=" + device.findObjects(By.checkable(true)).size)
  }
}
