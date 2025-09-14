package org.grakovne.lissen.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.activity.AppActivity
import org.grakovne.lissen.ui.navigation.CONTINUE_PLAYBACK
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuePlaybackShortcut
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: LissenSharedPreferences,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
      Log.d(TAG, "ContinuePlaybackShortcut registered")

      scope.launch {
        sharedPreferences
          .playingBookFlow
          .collect { updateShortcut(it) }
      }
    }

    private fun updateShortcut(playingBook: DetailedItem?) {
      Log.d(TAG, "ContinuePlaybackShortcut is updating")

      val shortcutManager = context.getSystemService(ShortcutManager::class.java)

      if (playingBook == null) {
        shortcutManager.removeDynamicShortcuts(listOf(SHORTCUT_TAG))
        return
      }

      val shortcut =
        ShortcutInfo
          .Builder(context, SHORTCUT_TAG)
          .setShortLabel(context.getString(R.string.continue_playback_shortcut_title))
          .setLongLabel(context.getString(R.string.continue_playback_shortcut_description))
          .setIcon(Icon.createWithResource(context, R.drawable.ic_play))
          .setIntent(
            Intent(context, AppActivity::class.java).apply {
              action = CONTINUE_PLAYBACK
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
          ).build()

      shortcutManager.dynamicShortcuts = listOf(shortcut)
    }

    companion object {
      private const val SHORTCUT_TAG = "continue_playback_shortcut"
      private const val TAG = "ContinuePlaybackShortcut"
    }
  }
