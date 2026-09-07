package org.grakovne.lissen.persistence.preferences

import kotlinx.coroutines.flow.Flow
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    val preferredLibraryTypeFlow: Flow<LibraryType> =
      store.asFlow(KEY_PREFERRED_LIBRARY_TYPE) { getPreferredLibrary()?.type ?: LibraryType.UNKNOWN }
    val hideCompletedFlow: Flow<Boolean> = store.asFlow(KEY_HIDE_COMPLETED, ::getHideCompleted)
    val libraryGroupingFlow: Flow<LibraryGrouping> = store.asFlow(KEY_LIBRARY_GROUPING, ::getLibraryGrouping)
    val forceCacheFlow: Flow<Boolean> = store.asFlow(CACHE_FORCE_ENABLED, ::isForceCache)

    fun getPreferredLibrary(): Library? {
      val id = activeLibraryId() ?: return null
      val name = getPreferredLibraryName() ?: return null

      return Library(
        id = id,
        title = name,
        type = getPreferredLibraryType(),
      )
    }

    fun savePreferredLibrary(library: Library) {
      store.putString(KEY_PREFERRED_LIBRARY_ID, library.id)
      store.putString(KEY_PREFERRED_LIBRARY_NAME, library.title)
      store.putString(KEY_PREFERRED_LIBRARY_TYPE, library.type.name)
    }

    fun activeLibraryId(): String? = store.getString(KEY_PREFERRED_LIBRARY_ID)

    fun saveLibraryOrdering(configuration: LibraryOrderingConfiguration) {
      val adapter = moshi.adapter(LibraryOrderingConfiguration::class.java)
      store.putString(KEY_PREFERRED_LIBRARY_ORDERING, adapter.toJson(configuration))
    }

    fun getLibraryOrdering(): LibraryOrderingConfiguration {
      val json = store.getString(KEY_PREFERRED_LIBRARY_ORDERING) ?: return LibraryOrderingConfiguration.default
      val adapter = moshi.adapter(LibraryOrderingConfiguration::class.java)
      return adapter.fromJson(json) ?: LibraryOrderingConfiguration.default
    }

    fun getHideCompleted(): Boolean = store.getBoolean(KEY_HIDE_COMPLETED, false)

    fun saveHideCompleted(value: Boolean) = store.putBoolean(KEY_HIDE_COMPLETED, value)

    fun getLibraryGrouping(): LibraryGrouping =
      store
        .getString(KEY_LIBRARY_GROUPING)
        ?.let { runCatching { LibraryGrouping.valueOf(it) }.getOrNull() }
        ?: LibraryGrouping.NONE

    fun saveLibraryGrouping(value: LibraryGrouping) = store.putString(KEY_LIBRARY_GROUPING, value.name)

    fun isForceCache(): Boolean = store.getBoolean(CACHE_FORCE_ENABLED, false)

    fun enableForceCache() = store.putBoolean(CACHE_FORCE_ENABLED, true)

    fun disableForceCache() = store.putBoolean(CACHE_FORCE_ENABLED, false)

    fun clearActive() {
      store.remove(
        listOf(
          CACHE_FORCE_ENABLED,
          KEY_PREFERRED_LIBRARY_ID,
          KEY_PREFERRED_LIBRARY_NAME,
          KEY_PREFERRED_LIBRARY_TYPE,
        ),
      )
    }

    private fun getPreferredLibraryName(): String? = store.getString(KEY_PREFERRED_LIBRARY_NAME)

    private fun getPreferredLibraryType(): LibraryType =
      store
        .getString(KEY_PREFERRED_LIBRARY_TYPE)
        ?.let { runCatching { LibraryType.valueOf(it) }.getOrNull() }
        ?: LibraryType.LIBRARY

    companion object {
      private const val CACHE_FORCE_ENABLED = "cache_force_enabled"
      private const val KEY_PREFERRED_LIBRARY_ID = "preferred_library_id"
      private const val KEY_PREFERRED_LIBRARY_NAME = "preferred_library_name"
      private const val KEY_PREFERRED_LIBRARY_TYPE = "preferred_library_type"
      private const val KEY_PREFERRED_LIBRARY_ORDERING = "preferred_library_ordering"
      private const val KEY_HIDE_COMPLETED = "hide_completed"
      private const val KEY_LIBRARY_GROUPING = "library_grouping"
    }
  }
