package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Types
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.domain.makeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LissenSharedPreferencesConfigBackupTest {
  private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
  private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
  private val context = mockk<Context>(relaxed = true)
  private lateinit var preferences: LissenSharedPreferences

  @BeforeEach
  fun setup() {
    every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
    every { sharedPreferences.edit() } returns editor

    // Echo the requested default value back for any key that isn't explicitly stubbed by a
    // test, so every getter falls through to its own documented default instead of a bare
    // MockK relaxed value (which would trip enum .valueOf() calls on an empty string).
    every { sharedPreferences.getString(any(), any()) } answers { secondArg() }
    every { sharedPreferences.getBoolean(any(), any()) } answers { secondArg() }
    every { sharedPreferences.getInt(any(), any()) } answers { secondArg() }
    every { sharedPreferences.getFloat(any(), any()) } answers { secondArg() }

    preferences = LissenSharedPreferences(context)
  }

  @Nested
  inner class ExportSettings {
    @Test
    fun `maps color scheme, material you, playback speed and volume boost`() {
      every { sharedPreferences.getString("preferred_color_scheme", any()) } returns "DARK"
      every { sharedPreferences.getBoolean("material_you_enabled", false) } returns true
      every { sharedPreferences.getFloat("preferred_playback_speed", 1f) } returns 1.5f
      every { sharedPreferences.getInt("volume_boost", 0) } returns 8

      val backup = preferences.exportSettings()

      assertEquals("DARK", backup.colorScheme)
      assertTrue(backup.materialYouEnabled == true)
      assertEquals(1.5f, backup.playbackSpeed)
      assertEquals(8, backup.volumeBoost)
    }

    @Test
    fun `maps seek time from stored json`() {
      every { sharedPreferences.getString("preferred_seek_time", null) } returns
        """{"rewind":15,"forward":45}"""

      val backup = preferences.exportSettings()

      assertEquals(SeekTime(rewind = 15, forward = 45), backup.seekTime)
    }

    @Test
    fun `maps equalizer from stored json`() {
      every { sharedPreferences.getString("equalizer", null) } returns
        """{"gains":[1,-2,0,3,6]}"""

      val backup = preferences.exportSettings()

      assertEquals(EqualizerSettings(gains = listOf(1, -2, 0, 3, 6)), backup.equalizer)
    }

    @Test
    fun `maps audio focus policy, software codecs, hide completed and library grouping`() {
      every { sharedPreferences.getString("audio_focus_loss_policy", any()) } returns "PAUSE"
      every { sharedPreferences.getBoolean("software_codecs", false) } returns true
      every { sharedPreferences.getBoolean("hide_completed", false) } returns true
      every { sharedPreferences.getString("library_grouping", any()) } returns "AUTHOR"

      val backup = preferences.exportSettings()

      assertEquals("PAUSE", backup.audioFocusLossPolicy)
      assertTrue(backup.softwareCodecsEnabled == true)
      assertTrue(backup.hideCompleted == true)
      assertEquals("AUTHOR", backup.libraryGrouping)
    }

    @Test
    fun `maps auto download option id via DownloadOption encoding`() {
      every { sharedPreferences.getString("preferred_auto_download", null) } returns "current_item"

      val backup = preferences.exportSettings()

      assertEquals("current_item", backup.autoDownloadOptionId)
    }

    @Test
    fun `maps auto download network type, delay and chapters count`() {
      every { sharedPreferences.getString("preferred_auto_download_network_type", any()) } returns "WIFI_OR_CELLULAR"
      every { sharedPreferences.getBoolean("auto_download_delayed", false) } returns true
      every { sharedPreferences.getInt("download_chapters_count", 5) } returns 10

      val backup = preferences.exportSettings()

      assertEquals("WIFI_OR_CELLULAR", backup.autoDownloadNetworkType)
      assertTrue(backup.autoDownloadDelayed == true)
      assertEquals(10, backup.downloadChaptersCount)
    }

    @Test
    fun `maps auto download library types list`() {
      every { sharedPreferences.getString("preferred_auto_download_library_type", null) } returns
        """["LIBRARY","PODCAST"]"""

      val backup = preferences.exportSettings()

      assertEquals(listOf("LIBRARY", "PODCAST"), backup.autoDownloadLibraryTypes)
    }

    @Test
    fun `maps library ordering configuration`() {
      val configuration =
        LibraryOrderingConfiguration(
          option = LibraryOrderingOption.AUTHOR,
          direction = LibraryOrderingDirection.DESCENDING,
        )
      every { sharedPreferences.getString("preferred_library_ordering", null) } returns
        moshi.adapter(LibraryOrderingConfiguration::class.java).toJson(configuration)

      val backup = preferences.exportSettings()

      assertEquals(configuration, backup.libraryOrdering)
    }

    @Test
    fun `maps default sleep timer episode option`() {
      every { sharedPreferences.getString("default_sleep_timer", null) } returns """{"type":"episode"}"""

      val backup = preferences.exportSettings()

      assertEquals("episode", backup.defaultSleepTimerType)
      assertNull(backup.defaultSleepTimerMinutes)
    }

    @Test
    fun `maps default sleep timer duration option`() {
      every { sharedPreferences.getString("default_sleep_timer", null) } returns
        """{"type":"duration","minutes":20}"""

      val backup = preferences.exportSettings()

      assertEquals("duration", backup.defaultSleepTimerType)
      assertEquals(20, backup.defaultSleepTimerMinutes)
    }

    @Test
    fun `maps null default sleep timer when nothing stored`() {
      every { sharedPreferences.getString("default_sleep_timer", null) } returns null

      val backup = preferences.exportSettings()

      assertNull(backup.defaultSleepTimerType)
      assertNull(backup.defaultSleepTimerMinutes)
    }

    @Test
    fun `maps crash reporting, activity logging, force cache, bypass ssl and user agent`() {
      every { sharedPreferences.getBoolean("acra.enable", true) } returns false
      every { sharedPreferences.getBoolean("activity_logging_enabled", true) } returns false
      every { sharedPreferences.getBoolean("cache_force_enabled", false) } returns true
      every { sharedPreferences.getBoolean("bypass_ssl", false) } returns true
      every { sharedPreferences.getString("user_agent", any()) } returns "CustomAgent/2.0"

      val backup = preferences.exportSettings()

      assertFalse(backup.crashReportingEnabled == true)
      assertFalse(backup.activityLoggingEnabled == true)
      assertTrue(backup.forceCacheEnabled == true)
      assertTrue(backup.bypassSsl == true)
      assertEquals("CustomAgent/2.0", backup.userAgent)
    }

    @Test
    fun `maps custom headers and local urls`() {
      val headers = listOf(ServerRequestHeader(name = "X-Token", value = "abc"))
      val urls = listOf(LocalUrl(ssid = "HomeWifi", route = "http://192.168.1.1"))

      val headersType = Types.newParameterizedType(List::class.java, ServerRequestHeader::class.java)
      val urlsType = Types.newParameterizedType(List::class.java, LocalUrl::class.java)

      every { sharedPreferences.getString("custom_headers", null) } returns
        moshi.adapter<List<ServerRequestHeader>>(headersType).toJson(headers)
      every { sharedPreferences.getString("local_urls", null) } returns
        moshi.adapter<List<LocalUrl>>(urlsType).toJson(urls)

      val backup = preferences.exportSettings()

      assertEquals(headers, backup.customHeaders)
      assertEquals(urls, backup.localUrls)
    }

    @Test
    fun `falls back to documented defaults when nothing is stored`() {
      val backup = preferences.exportSettings()

      assertEquals(ColorScheme.FOLLOW_SYSTEM.name, backup.colorScheme)
      assertEquals(AudioFocusLossPolicy.LOWER_VOLUME.name, backup.audioFocusLossPolicy)
      assertEquals(LibraryGrouping.NONE.name, backup.libraryGrouping)
      assertEquals(NetworkTypeAutoCache.WIFI_ONLY.name, backup.autoDownloadNetworkType)
      assertEquals("disabled", backup.autoDownloadOptionId)
      assertEquals(listOf(LibraryType.LIBRARY.name, LibraryType.PODCAST.name), backup.autoDownloadLibraryTypes)
      assertEquals(SeekTime.Default, backup.seekTime)
      assertEquals(EqualizerSettings.Default, backup.equalizer)
      assertEquals(LibraryOrderingConfiguration.default, backup.libraryOrdering)
      assertEquals(DEFAULT_USER_AGENT, backup.userAgent)
      assertEquals(emptyList<ServerRequestHeader>(), backup.customHeaders)
      assertEquals(emptyList<LocalUrl>(), backup.localUrls)
      assertNull(backup.defaultSleepTimerType)
      assertNull(backup.defaultSleepTimerMinutes)
    }
  }

  @Nested
  inner class ImportSettings {
    @Test
    fun `saves color scheme when valid`() {
      preferences.importSettings(SettingsBackup(colorScheme = "DARK"))
      verify { editor.putString("preferred_color_scheme", "DARK") }
    }

    @Test
    fun `ignores invalid color scheme string`() {
      preferences.importSettings(SettingsBackup(colorScheme = "NOT_A_SCHEME"))
      verify(exactly = 0) { editor.putString("preferred_color_scheme", any()) }
    }

    @Test
    fun `skips color scheme when absent`() {
      preferences.importSettings(SettingsBackup(colorScheme = null))
      verify(exactly = 0) { editor.putString("preferred_color_scheme", any()) }
    }

    @Test
    fun `ignores invalid audio focus policy string`() {
      preferences.importSettings(SettingsBackup(audioFocusLossPolicy = "NOT_A_POLICY"))
      verify(exactly = 0) { editor.putString("audio_focus_loss_policy", any()) }
    }

    @Test
    fun `ignores invalid library grouping string`() {
      preferences.importSettings(SettingsBackup(libraryGrouping = "NOT_A_GROUPING"))
      verify(exactly = 0) { editor.putString("library_grouping", any()) }
    }

    @Test
    fun `ignores invalid auto download network type string`() {
      preferences.importSettings(SettingsBackup(autoDownloadNetworkType = "NOT_A_TYPE"))
      verify(exactly = 0) { editor.putString("preferred_auto_download_network_type", any()) }
    }

    @Test
    fun `saves scalar preferences when present`() {
      preferences.importSettings(
        SettingsBackup(
          materialYouEnabled = true,
          playbackSpeed = 1.75f,
          volumeBoost = 5,
          softwareCodecsEnabled = true,
          hideCompleted = true,
          autoDownloadDelayed = true,
          downloadChaptersCount = 12,
          crashReportingEnabled = false,
          activityLoggingEnabled = false,
          bypassSsl = true,
          userAgent = "CustomAgent/2.0",
        ),
      )

      verify { editor.putBoolean("material_you_enabled", true) }
      verify { editor.putFloat("preferred_playback_speed", 1.75f) }
      verify { editor.putInt("volume_boost", 5) }
      verify { editor.putBoolean("software_codecs", true) }
      verify { editor.putBoolean("hide_completed", true) }
      verify { editor.putBoolean("auto_download_delayed", true) }
      verify { editor.putInt("download_chapters_count", 12) }
      verify { editor.putBoolean("acra.enable", false) }
      verify { editor.putBoolean("activity_logging_enabled", false) }
      verify { editor.putBoolean("bypass_ssl", true) }
      verify { editor.putString("user_agent", "CustomAgent/2.0") }
    }

    @Test
    fun `saves seek time when present`() {
      preferences.importSettings(SettingsBackup(seekTime = SeekTime(rewind = 5, forward = 90)))

      verify {
        editor.putString(
          "preferred_seek_time",
          match { it.contains("\"rewind\":5") && it.contains("\"forward\":90") },
        )
      }
    }

    @Test
    fun `saves equalizer when present`() {
      preferences.importSettings(
        SettingsBackup(equalizer = EqualizerSettings(gains = listOf(2, 0, -3))),
      )

      verify {
        editor.putString(
          "equalizer",
          match { it.contains("[2,0,-3]") },
        )
      }
    }

    @Test
    fun `skips equalizer when absent`() {
      preferences.importSettings(SettingsBackup(equalizer = null))
      verify(exactly = 0) { editor.putString("equalizer", any()) }
    }

    @Test
    fun `saves library ordering when present`() {
      val configuration = LibraryOrderingConfiguration(LibraryOrderingOption.CREATED_AT, LibraryOrderingDirection.DESCENDING)

      preferences.importSettings(SettingsBackup(libraryOrdering = configuration))

      verify {
        editor.putString(
          "preferred_library_ordering",
          match { it.contains("CREATED_AT") && it.contains("DESCENDING") },
        )
      }
    }

    @Test
    fun `maps auto download option id all_items to AllItemsDownloadOption`() {
      preferences.importSettings(SettingsBackup(autoDownloadOptionId = "all_items"))
      verify { editor.putString("preferred_auto_download", AllItemsDownloadOption.makeId()) }
    }

    @Test
    fun `maps auto download option id disabled to null option`() {
      // saveAutoDownloadOption(option) writes "option?.makeId()" - a null option short-circuits
      // the safe call and stores null directly, rather than invoking makeId() on it.
      preferences.importSettings(SettingsBackup(autoDownloadOptionId = "disabled"))
      verify { editor.putString("preferred_auto_download", null) }
    }

    @Test
    fun `skips auto download option id when absent`() {
      preferences.importSettings(SettingsBackup(autoDownloadOptionId = null))
      verify(exactly = 0) { editor.putString("preferred_auto_download", any()) }
    }

    @Test
    fun `filters invalid auto download library types`() {
      preferences.importSettings(SettingsBackup(autoDownloadLibraryTypes = listOf("LIBRARY", "NOT_A_TYPE")))

      verify {
        editor.putString(
          "preferred_auto_download_library_type",
          match { it.contains("LIBRARY") && !it.contains("NOT_A_TYPE") },
        )
      }
    }

    @Test
    fun `saves default sleep timer episode option`() {
      preferences.importSettings(SettingsBackup(defaultSleepTimerType = "episode"))

      verify {
        editor.putString("default_sleep_timer", match { it.contains("episode") })
      }
    }

    @Test
    fun `saves default sleep timer duration option with minutes`() {
      preferences.importSettings(SettingsBackup(defaultSleepTimerType = "duration", defaultSleepTimerMinutes = 20))

      verify {
        editor.putString(
          "default_sleep_timer",
          match { it.contains("duration") && it.contains("20") },
        )
      }
    }

    @Test
    fun `skips default sleep timer when type is absent`() {
      preferences.importSettings(SettingsBackup(defaultSleepTimerType = null))
      verify(exactly = 0) { editor.putString("default_sleep_timer", any()) }
      verify(exactly = 0) { editor.remove("default_sleep_timer") }
    }

    @Test
    fun `enables force cache when true`() {
      preferences.importSettings(SettingsBackup(forceCacheEnabled = true))
      verify { editor.putBoolean("cache_force_enabled", true) }
    }

    @Test
    fun `disables force cache when false`() {
      preferences.importSettings(SettingsBackup(forceCacheEnabled = false))
      verify { editor.putBoolean("cache_force_enabled", false) }
    }

    @Test
    fun `leaves force cache untouched when absent`() {
      preferences.importSettings(SettingsBackup(forceCacheEnabled = null))
      verify(exactly = 0) { editor.putBoolean("cache_force_enabled", any()) }
    }

    @Test
    fun `saves custom headers and local urls when present`() {
      val headers = listOf(ServerRequestHeader(name = "X-Token", value = "abc"))
      val urls = listOf(LocalUrl(ssid = "HomeWifi", route = "http://192.168.1.1"))

      preferences.importSettings(SettingsBackup(customHeaders = headers, localUrls = urls))

      verify { editor.putString("custom_headers", match { it.contains("X-Token") }) }
      verify { editor.putString("local_urls", match { it.contains("HomeWifi") }) }
    }

    @Test
    fun `all-null backup writes nothing to preferences`() {
      preferences.importSettings(SettingsBackup())

      verify(exactly = 0) { editor.putString(any(), any()) }
      verify(exactly = 0) { editor.putBoolean(any(), any()) }
      verify(exactly = 0) { editor.putInt(any(), any()) }
      verify(exactly = 0) { editor.putFloat(any(), any()) }
    }
  }
}
