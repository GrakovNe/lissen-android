package org.grakovne.lissen.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import javax.inject.Inject

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
  lateinit var preferencesReset: PreferencesReset

  @get:Rule(order = 2)
  val setupRule =
    object : ExternalResource() {
      override fun before() {
        hiltRule.inject()
        preferencesReset.clearAll()
        E2ESession.restore()
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private fun login() {
    composeRule.loginToLibrary()
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

    composeRule.onNodeWithTag("librarySearchField").performTextInput(E2E_SEARCH_QUERY)

    composeRule.waitUntilBookItemsExist()

    composeRule.onAllNodes(bookItemMatcher)[0].assertIsDisplayed()
  }
}
