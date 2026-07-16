package org.grakovne.lissen.persistence.preferences

import com.squareup.moshi.Types
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.makeDownloadOption
import org.grakovne.lissen.domain.makeId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    fun getAutoDownloadDelayed(): Boolean = store.getBoolean(KEY_AUTO_DOWNLOAD_DELAYED, false)

    fun saveAutoDownloadDelayed(enabled: Boolean) = store.putBoolean(KEY_AUTO_DOWNLOAD_DELAYED, enabled)

    fun getAutoDownloadNetworkType(): NetworkTypeAutoCache =
      store
        .getString(KEY_PREFERRED_AUTO_DOWNLOAD_NETWORK_TYPE, NetworkTypeAutoCache.WIFI_ONLY.name)
        ?.let { runCatching { NetworkTypeAutoCache.valueOf(it) }.getOrNull() }
        ?: NetworkTypeAutoCache.WIFI_ONLY

    fun saveAutoDownloadNetworkType(networkTypeAutoCache: NetworkTypeAutoCache) =
      store.putString(KEY_PREFERRED_AUTO_DOWNLOAD_NETWORK_TYPE, networkTypeAutoCache.name)

    fun getAutoDownloadLibraryTypes(): List<LibraryType> {
      val json = store.getString(KEY_PREFERRED_AUTO_DOWNLOAD_LIBRARY_TYPE) ?: return LibraryType.meaningfulTypes
      val type = Types.newParameterizedType(List::class.java, LibraryType::class.java)
      val adapter = moshi.adapter<List<LibraryType>>(type)
      return adapter.fromJson(json) ?: LibraryType.meaningfulTypes
    }

    fun saveAutoDownloadLibraryTypes(types: List<LibraryType>) {
      val type = Types.newParameterizedType(List::class.java, LibraryType::class.java)
      val adapter = moshi.adapter<List<LibraryType>>(type)
      store.putString(KEY_PREFERRED_AUTO_DOWNLOAD_LIBRARY_TYPE, adapter.toJson(types))
    }

    fun getAutoDownloadOption(): DownloadOption? =
      store
        .getString(KEY_PREFERRED_AUTO_DOWNLOAD)
        ?.makeDownloadOption()

    fun saveAutoDownloadOption(option: DownloadOption?) =
      when (option) {
        null -> store.remove(KEY_PREFERRED_AUTO_DOWNLOAD)
        else -> store.putString(KEY_PREFERRED_AUTO_DOWNLOAD, option.makeId())
      }

    fun getDownloadChaptersCount(): Int = store.getInt(KEY_DOWNLOAD_CHAPTERS_COUNT, DEFAULT_DOWNLOAD_CHAPTERS_COUNT)

    fun saveDownloadChaptersCount(count: Int) = store.putInt(KEY_DOWNLOAD_CHAPTERS_COUNT, count)

    companion object {
      private const val KEY_AUTO_DOWNLOAD_DELAYED = "auto_download_delayed"
      private const val KEY_PREFERRED_AUTO_DOWNLOAD = "preferred_auto_download"
      private const val KEY_PREFERRED_AUTO_DOWNLOAD_NETWORK_TYPE = "preferred_auto_download_network_type"
      private const val KEY_PREFERRED_AUTO_DOWNLOAD_LIBRARY_TYPE = "preferred_auto_download_library_type"
      private const val KEY_DOWNLOAD_CHAPTERS_COUNT = "download_chapters_count"
      private const val DEFAULT_DOWNLOAD_CHAPTERS_COUNT = 5
    }
  }
