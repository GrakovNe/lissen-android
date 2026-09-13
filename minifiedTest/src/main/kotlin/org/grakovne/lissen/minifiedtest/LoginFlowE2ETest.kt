package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.shell.Shell
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.boundsInScreen
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.uiAutomator
import androidx.test.uiautomator.watcher.PermissionDialog
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginFlowE2ETest {
  @Test
  fun loginScreen_allInputFieldsAreVisible() = withFreshApp {
    onElement { viewIdResourceName == "hostInput" }
    onElement { viewIdResourceName == "usernameInput" }
    onElement { viewIdResourceName == "passwordInput" }
    onElement { viewIdResourceName == "loginButton" }
  }

  @Test
  fun loginWithValidCredentials_navigatesToLibrary() = withFreshApp {
    login(password = e2eArgument("e2ePassword", "demo"))
    onElement(TIMEOUT_MS) { viewIdResourceName == "libraryScreen" }
  }

  @Test
  fun loginWithWrongPassword_staysOnLoginScreen() = withFreshApp {
    login(password = "wrong_password_xyz")
    onElement(TIMEOUT_MS) { viewIdResourceName == "loginButton" }
  }

  @Test
  fun loginWithEmptyCredentials_staysOnLoginScreen() = withFreshApp {
    onElement { viewIdResourceName == "loginButton" }.click()
    assertNull(onElementOrNull(SHORT_TIMEOUT_MS) { viewIdResourceName == "libraryScreen" })
    onElement { viewIdResourceName == "loginButton" }
  }

  @Test
  fun settingsButtonOnLoginScreen_opensAndClosesSettings() = withFreshApp {
    onElement { viewIdResourceName == "loginSettingsButton" }.click()
    onElement(TIMEOUT_MS) { viewIdResourceName == "settingsScreen" }
    pressBack()
    onElement(TIMEOUT_MS) { viewIdResourceName == "loginScreen" }
    onElement { viewIdResourceName == "loginButton" }
  }

  @Test
  fun sessionSurvivesAppRestart() = withFreshApp {
    login(password = e2eArgument("e2ePassword", "demo"))
    onElement(TIMEOUT_MS) { viewIdResourceName == "libraryScreen" }
    device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    onElement(TIMEOUT_MS) { viewIdResourceName == "libraryScreen" || viewIdResourceName == "playerScreen" }
    assertNull(onElementOrNull(SHORT_TIMEOUT_MS) { viewIdResourceName == "loginButton" })
  }

  @Test
  fun disconnectFromServer_returnsToLoginScreen() = withFreshApp {
    login(password = e2eArgument("e2ePassword", "demo"))
    onElement(TIMEOUT_MS) { viewIdResourceName == "libraryScreen" }
    clickElement(By.desc("Menu"))
    clickElement(By.text("Application settings"))
    waitForElement(By.res("settingsScreen"))
    clickElement(By.text("Connection"))
    scrollUntilVisible(By.text("Disconnect from the server")).click()
    clickElement(By.text("Disconnect"))
    waitForElement(By.res("loginScreen"))
  }

  private fun withFreshApp(block: UiAutomatorTestScope.() -> Unit) = uiAutomator {
    Shell.application.clearAppData(TARGET_PACKAGE)
    watchFor(PermissionDialog) { clickAllow() }
    startApp(TARGET_PACKAGE)
    waitForAppToBeVisible(TARGET_PACKAGE)
    block()
  }

  private fun UiAutomatorTestScope.login(password: String) {
    onElement { viewIdResourceName == "hostInput" }
      .setText(e2eArgument("e2eHost", "https://demo.lissenapp.org"))
    onElement { viewIdResourceName == "usernameInput" }
      .setText(e2eArgument("e2eUsername", "demo"))
    onElement { viewIdResourceName == "passwordInput" }.setText(password)
    onElement { viewIdResourceName == "loginButton" }.click()
  }


  private fun e2eArgument(name: String, fallback: String): String =
    InstrumentationRegistry.getArguments().getString(name) ?: fallback

  private companion object {
    const val TARGET_PACKAGE = "org.grakovne.lissen.minified"
    const val TIMEOUT_MS = 45_000L
    const val SHORT_TIMEOUT_MS = 5_000L
  }
}
