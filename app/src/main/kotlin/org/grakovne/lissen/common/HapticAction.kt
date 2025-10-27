package org.grakovne.lissen.common

import android.view.HapticFeedbackConstants
import android.view.View

fun withHaptic(
  view: View,
  action: () -> Unit,
) {
  action()
  view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
