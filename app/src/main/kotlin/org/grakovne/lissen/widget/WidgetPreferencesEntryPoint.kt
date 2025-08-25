package org.grakovne.lissen.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetPreferencesEntryPoint {
  fun lissenSharedPreferences(): LissenSharedPreferences
}
