package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.common.api.encodeLibraryFilter
import org.grakovne.lissen.channel.common.LibraryFilters
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryFilteringRequestConverter
  @Inject
  constructor() {
    fun apply(preferences: LibraryPreferences): String? =
      when (preferences.getHideCompleted()) {
        true -> encodeLibraryFilter(LibraryFilters.PROGRESS, LibraryFilters.NOT_FINISHED)
        false -> null
      }
  }
