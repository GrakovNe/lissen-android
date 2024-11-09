package org.grakovne.lissen.content

import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCacheConfiguration @Inject constructor(
    private val sharedPreferences: LissenSharedPreferences
) {

    fun localCacheUsing(): Boolean = sharedPreferences.isForceCache()
}
