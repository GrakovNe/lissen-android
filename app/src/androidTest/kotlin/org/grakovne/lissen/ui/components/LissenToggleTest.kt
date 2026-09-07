package org.grakovne.lissen.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class LissenToggleTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun reflectsCheckedState() {
    composeRule.setContent {
      LissenToggle(checked = true, onCheckedChange = {})
    }

    composeRule.onNode(isToggleable()).assertIsOn()
  }

  @Test
  fun reflectsUncheckedState() {
    composeRule.setContent {
      LissenToggle(checked = false, onCheckedChange = {})
    }

    composeRule.onNode(isToggleable()).assertIsOff()
  }

  @Test
  fun clickTogglesAndReportsNewValue() {
    val received = mutableListOf<Boolean>()

    composeRule.setContent {
      var checked by remember { mutableStateOf(false) }
      LissenToggle(
        checked = checked,
        onCheckedChange = {
          checked = it
          received += it
        },
      )
    }

    composeRule.onNode(isToggleable()).assertIsOff()
    composeRule.onNode(isToggleable()).performClick()
    composeRule.onNode(isToggleable()).assertIsOn()
    composeRule.onNode(isToggleable()).performClick()
    composeRule.onNode(isToggleable()).assertIsOff()

    assert(received == listOf(true, false)) { "unexpected callbacks: $received" }
  }

  @Test
  fun disabledToggleDoesNotReact() {
    var clicked = false

    composeRule.setContent {
      LissenToggle(checked = false, enabled = false, onCheckedChange = { clicked = true })
    }

    composeRule.onNode(isToggleable()).assertIsNotEnabled()
    composeRule.onNode(isToggleable()).performClick()

    assert(!clicked) { "disabled toggle should not invoke onCheckedChange" }
  }

  @Test
  fun enabledToggleIsEnabled() {
    composeRule.setContent {
      LissenToggle(checked = true, enabled = true, onCheckedChange = {})
    }

    composeRule.onNode(isToggleable()).assertIsEnabled()
  }
}
