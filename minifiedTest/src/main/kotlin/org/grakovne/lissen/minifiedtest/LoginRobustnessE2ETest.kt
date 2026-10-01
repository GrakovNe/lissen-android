package org.grakovne.lissen.minifiedtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginRobustnessE2ETest {
  @Test
  fun loginWithUnreachableHost_staysOnLoginScreenAlive() = freshApp {
    setTextOf(By.res("hostInput"), "http://127.0.0.1:9")
    setTextOf(By.res("usernameInput"), "e2e")
    setTextOf(By.res("passwordInput"), "e2e")
    clickElement(By.res("loginButton"))
    // a refused connection fails fast; any other outcome must still leave the form
    Thread.sleep(5_000)
    assertOnLoginForm()
    assertAppAlive()
  }

  @Test
  fun loginWithMalformedHost_staysOnLoginScreenAlive() = freshApp {
    setTextOf(By.res("hostInput"), "not a url")
    setTextOf(By.res("usernameInput"), "e2e")
    setTextOf(By.res("passwordInput"), "e2e")
    clickElement(By.res("loginButton"))
    Thread.sleep(5_000)
    assertOnLoginForm()
    assertAppAlive()
  }

  private fun UiAutomatorTestScope.assertOnLoginForm() {
    assertNotNull("login button must still be present", device.findObject(By.res("loginButton")))
    assertNotNull("host field must still be present", device.findObject(By.res("hostInput")))
  }

  private fun UiAutomatorTestScope.assertAppAlive() {
    val pid = device.executeShellCommand("pidof $TARGET_PACKAGE").trim()
    if (pid.isEmpty()) throw AssertionError("$TARGET_PACKAGE crashed while logging in with a bad host")
  }
}
