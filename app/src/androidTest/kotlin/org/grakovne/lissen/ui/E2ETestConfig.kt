package org.grakovne.lissen.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry

internal val E2E_HOST: String
  get() = InstrumentationRegistry.getArguments().getString("e2eHost") ?: "https://demo.lissenapp.org"

internal val E2E_USERNAME: String
  get() = InstrumentationRegistry.getArguments().getString("e2eUsername") ?: "demo"

internal val E2E_PASSWORD: String
  get() = InstrumentationRegistry.getArguments().getString("e2ePassword") ?: "demo"

internal val E2E_SEARCH_QUERY: String
  get() = InstrumentationRegistry.getArguments().getString("e2eSearchQuery") ?: "a"

internal const val TIMEOUT_MS = 45_000L

internal val bookItemMatcher =
  SemanticsMatcher("hasBookItemTag") { node ->
    node.config
      .getOrElseNullable(SemanticsProperties.TestTag) { null }
      ?.startsWith("bookItem_") == true
  }

internal object E2ESession {
  private var snapshot: Map<String, Any?>? = null

  val available: Boolean
    get() = snapshot != null

  fun capture() {
    snapshot = securePrefs().all.toMap()
  }

  fun restore() {
    val data = snapshot ?: return

    securePrefs().edit(commit = true) {
      data.forEach { (key, value) ->
        when (value) {
          is String -> putString(key, value)
          is Boolean -> putBoolean(key, value)
          is Int -> putInt(key, value)
          is Long -> putLong(key, value)
          is Float -> putFloat(key, value)
          is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
      }
    }
  }

  private fun securePrefs() =
    InstrumentationRegistry
      .getInstrumentation()
      .targetContext
      .getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.loginToLibrary(scrollToButton: Boolean = false) {
  if (E2ESession.available) {
    waitUntilAtLeastOneExists(
      matcher = hasTestTag("libraryScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    return
  }

  onNodeWithTag("hostInput").performTextInput(E2E_HOST)
  onNodeWithTag("usernameInput").performTextInput(E2E_USERNAME)
  onNodeWithTag("passwordInput").performTextInput(E2E_PASSWORD)

  val loginButton = onNodeWithTag("loginButton")
  if (scrollToButton) {
    loginButton.performScrollTo()
  }
  loginButton.performClick()

  waitUntilAtLeastOneExists(
    matcher = hasTestTag("libraryScreen"),
    timeoutMillis = TIMEOUT_MS,
  )
  waitUntilBookItemsExist()

  E2ESession.capture()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.waitUntilBookItemsExist(
  attempts: Int = 3,
  attemptTimeoutMillis: Long = 15_000L,
) {
  repeat(attempts) { attempt ->
    try {
      waitUntilAtLeastOneExists(bookItemMatcher, attemptTimeoutMillis)
      return
    } catch (exception: ComposeTimeoutException) {
      val revealedByScroll =
        runCatching {
          onNodeWithTag("libraryGrid").performScrollToNode(bookItemMatcher)
        }.isSuccess

      if (revealedByScroll) {
        return
      }

      if (attempt == attempts - 1) {
        throw exception
      }

      onNodeWithTag("libraryGrid").performTouchInput { swipeDown() }
    }
  }
}
