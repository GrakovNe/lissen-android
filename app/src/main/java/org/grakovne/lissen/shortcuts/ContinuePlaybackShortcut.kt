package org.grakovne.lissen.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.R
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.activity.AppActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuePlaybackShortcut @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: LissenSharedPreferences,
) : RunningComponent {

    override fun onCreate() {
        Log.d(TAG, "ContinuePlaybackShortcut registered")

        val playingBook = sharedPreferences.getPlayingBook()
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)

        shortcutManager.removeAllDynamicShortcuts()

        if (playingBook == null) {
            shortcutManager.removeDynamicShortcuts(listOf(SHORTCUT_TAG))
            return
        }

        val shortcut = ShortcutInfo
            .Builder(context, SHORTCUT_TAG)
            .setShortLabel("Continue")
            .setLongLabel("Continue listening")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_play))
            .setIntent(
                Intent(context, AppActivity::class.java).apply {
                    action = "continue_playback"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            .build()

        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }

    companion object {

        private const val SHORTCUT_TAG = "continue_playback_shortcut"
        private const val TAG = "ContinuePlaybackShortcut"
    }
}
