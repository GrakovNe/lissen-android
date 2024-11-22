package org.grakovne.lissen.usecases

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class LibraryNavigationTest {
    private val resetRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                context.cacheDir.deleteRecursively()
                context.filesDir.deleteRecursively()
                base.evaluate()
            }
        }
    }

    private val composeTestRule = createAndroidComposeRule<AppActivity>()

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(POST_NOTIFICATIONS)

    @get:Rule
    val chain: TestRule = RuleChain
        .outerRule(resetRule)
        .around(permissionRule)
        .around(composeTestRule)

    @Test
    fun should_open_first_book_in_list() {
        composeTestRule.onNodeWithTag("hostInput").performTextInput("https://demo.lissenapp.org")
        composeTestRule.onNodeWithTag("usernameInput").performTextInput("autotest")
        composeTestRule.onNodeWithTag("passwordInput").performTextInput("autotest")
        composeTestRule.onNodeWithTag("loginButton").performClick()

        composeTestRule.waitUntil(
            timeoutMillis = 2000,
            condition = {
                composeTestRule.onAllNodesWithTag("libraryScreen").fetchSemanticsNodes().isNotEmpty()
            },
        )

        composeTestRule.onNodeWithTag("libraryScreen").assertExists()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTagStartingWith("bookItem_")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onAllNodes(hasTestTagStartingWith("bookItem_")).onFirst().performClick()

        composeTestRule.waitUntil(
            timeoutMillis = 2000,
            condition = {
                composeTestRule.onAllNodesWithTag("playerScreen").fetchSemanticsNodes().isNotEmpty()
            },
        )

        composeTestRule.onNodeWithTag("playerScreen").assertExists()
    }

    private fun hasTestTagStartingWith(prefix: String): SemanticsMatcher =
        SemanticsMatcher("has test tag starting with $prefix") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) ?: false
        }
}
