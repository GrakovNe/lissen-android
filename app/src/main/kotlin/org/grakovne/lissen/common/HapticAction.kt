package org.grakovne.lissen.common

import android.view.HapticFeedbackConstants
import android.view.View
import timber.log.Timber

fun withHaptic(
  view: View,
  action: () -> Unit,
) {
  action()
  try {
    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
  } catch (ex: Exception) {
    Timber.w(ex)
  }
}
