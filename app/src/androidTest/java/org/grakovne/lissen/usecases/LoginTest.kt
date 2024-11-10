package org.grakovne.lissen.usecases

import android.Manifest.permission.POST_NOTIFICATIONS
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<AppActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(POST_NOTIFICATIONS)

    @Test
    fun should_login_with_proper_credentials() {
        composeTestRule.onNodeWithTag("hostInput").performTextInput("https://demo.lissenapp.org")
        composeTestRule.onNodeWithTag("usernameInput").performTextInput("autotest")
        composeTestRule.onNodeWithTag("passwordInput").performTextInput("autotest")
        composeTestRule.onNodeWithTag("loginButton").performClick()

        composeTestRule.waitUntil(
            timeoutMillis = 2000,
            condition = {
                composeTestRule.onAllNodesWithTag("libraryScreen").fetchSemanticsNodes().isNotEmpty()
            }
        )

        composeTestRule.onNodeWithTag("libraryScreen").assertExists()
    }
}
