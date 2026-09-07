package org.grakovne.lissen.persistence.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesReset
  @Inject
  constructor(
    private val session: SessionPreferences,
    private val connection: ConnectionPreferences,
    private val library: LibraryPreferences,
    private val playback: PlaybackPreferences,
  ) {
    fun clearAll() {
      session.clear()
      connection.clear()
      library.clearActive()
      playback.clearPlayingItems()
    }
  }
