package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryFilteringRequestConverter
  @Inject
  constructor() {
    fun apply(preferences: LissenSharedPreferences): String? {
      val hideCompleted = preferences.getHideCompleted()

      if (hideCompleted) {
        return "progress.bm90LWZpbmlzaGVk" // not-finished
      }

      return null
    }
  }
