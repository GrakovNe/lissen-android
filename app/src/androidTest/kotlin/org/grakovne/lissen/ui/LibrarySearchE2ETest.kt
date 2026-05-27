package org.grakovne.lissen.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import javax.inject.Inject

private const val DEMO_HOST = "https://demo.lissenapp.org"
private const val DEMO_USERNAME = "demo"
private const val DEMO_PASSWORD = "demo"
private const val TIMEOUT_MS = 30_000L

private val bookItemMatcher =
  SemanticsMatcher("hasBookItemTag") { node ->
    node.config
      .getOrElseNullable(SemanticsProperties.TestTag) { null }
      ?.startsWith("bookItem_") == true
  }

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LibrarySearchE2ETest {
  @get:Rule(order = 0)
  val grantPermissionsRule: GrantPermissionRule =
    GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 1)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var preferences: LissenSharedPreferences

  @get:Rule(order = 2)
  val setupRule =
    object : ExternalResource() {
      override fun before() {
        hiltRule.inject()
        preferences.clearPreferences()
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private fun login() {
    composeRule.onNodeWithTag("hostInput").performTextInput(DEMO_HOST)
    composeRule.onNodeWithTag("usernameInput").performTextInput(DEMO_USERNAME)
    composeRule.onNodeWithTag("passwordInput").performTextInput(DEMO_PASSWORD)
    composeRule.onNodeWithTag("loginButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("libraryScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  @Test
  fun search_fieldAppearsOnTap() {
    login()

    composeRule.onNodeWithContentDescription("Search").performClick()

    composeRule.onNodeWithTag("librarySearchField").assertIsDisplayed()
  }

  @Test
  fun search_showsResultsForQuery() {
    login()

    composeRule.onNodeWithContentDescription("Search").performClick()

    composeRule.onNodeWithTag("librarySearchField").performTextInput("a")

    composeRule.waitUntilAtLeastOneExists(
      matcher = bookItemMatcher,
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onAllNodes(bookItemMatcher)[0].assertIsDisplayed()
  }
}
