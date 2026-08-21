package org.grakovne.lissen.persistence.preferences

import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.RewindOnPauseTime
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.TimerOption
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
    private val libraryPreferences: LibraryPreferences,
  ) {
    private val playingItemLock = Any()
    private val playingItems = CachedValue { readPlayingItems() }

    private val bookLastActiveLock = Any()
    private val bookLastActive = CachedValue { readBookLastActive() }

    val playingItemFlow: Flow<DetailedItem?> = store.asFlow(KEY_PLAYING_ITEM, ::getPlayingItem)
    val playbackVolumeBoostFlow: Flow<Int> = store.asFlow(KEY_VOLUME_BOOST, ::getPlaybackVolumeBoost)
    val audioFocusLossPolicyFlow: Flow<AudioFocusLossPolicy> = store.asFlow(KEY_AUDIO_FOCUS_LOSS_POLICY, ::getAudioFocusLossPolicy)
    val equalizerFlow: Flow<EqualizerSettings> = store.asFlow(KEY_EQUALIZER, ::getEqualizer)
    val rewindOnPauseTimeFlow: Flow<RewindOnPauseTime> = store.asFlow(KEY_REWIND_ON_PAUSE_TIME, ::getRewindOnPauseTime)

    fun getPlaybackVolumeBoost(): Int =
      try {
        store.getInt(KEY_VOLUME_BOOST, 0)
      } catch (e: ClassCastException) {
        Timber.w("Stored volume boost has wrong type, resetting due to: ${e.message}")
        store.remove(KEY_VOLUME_BOOST)
        0
      }

    fun savePlaybackVolumeBoost(db: Int) = store.putInt(KEY_VOLUME_BOOST, db)

    fun getPlaybackSpeed(): Float = store.getFloat(KEY_PREFERRED_PLAYBACK_SPEED, 1f)

    fun savePlaybackSpeed(factor: Float) = store.putFloat(KEY_PREFERRED_PLAYBACK_SPEED, factor)

    fun getSoftwareCodecsEnabled(): Boolean = store.getBoolean(KEY_SOFTWARE_CODECS, false)

    fun saveSoftwareCodecsEnabled(value: Boolean) = store.putBoolean(KEY_SOFTWARE_CODECS, value)

    fun getAudioFocusLossPolicy(): AudioFocusLossPolicy =
      store
        .getString(KEY_AUDIO_FOCUS_LOSS_POLICY)
        ?.let { runCatching { AudioFocusLossPolicy.valueOf(it) }.getOrNull() }
        ?: AudioFocusLossPolicy.LOWER_VOLUME

    fun saveAudioFocusLossPolicy(policy: AudioFocusLossPolicy) = store.putString(KEY_AUDIO_FOCUS_LOSS_POLICY, policy.name)

    fun savePlayingItem(item: DetailedItem) {
      savePlayingItemInternal(
        libraryId = item.libraryId ?: return,
        item = item,
      )
    }

    fun clearPlayingItem() {
      val libraryId = libraryPreferences.activeLibraryId() ?: return
      savePlayingItemInternal(libraryId = libraryId, item = null)
    }

    fun getPlayingItem(): DetailedItem? {
      val libraryId = libraryPreferences.activeLibraryId() ?: return null
      return playingItems.get()[libraryId]
    }

    fun clearPlayingItems() {
      synchronized(playingItemLock) {
        store.remove(KEY_PLAYING_ITEM)
        playingItems.invalidate()
      }
    }

    fun getSeekTime(): SeekTime {
      val json = store.getString(KEY_PREFERRED_SEEK_TIME) ?: return SeekTime.Default
      return try {
        moshi.adapter(SeekTime::class.java).fromJson(json) ?: SeekTime.Default
      } catch (e: com.squareup.moshi.JsonDataException) {
        Timber.w("Stored seek time is malformed, resetting due to: ${e.message}")
        store.remove(KEY_PREFERRED_SEEK_TIME, commit = true)
        SeekTime.Default
      }
    }

    fun saveSeekTime(seekTime: SeekTime) {
      val json = moshi.adapter(SeekTime::class.java).toJson(seekTime)
      store.putString(KEY_PREFERRED_SEEK_TIME, json, commit = true)
    }

    fun getRewindOnPauseTime(): RewindOnPauseTime {
      val json = store.getString(KEY_REWIND_ON_PAUSE_TIME) ?: return RewindOnPauseTime.Default
      return try {
        val parsed = moshi.adapter(RewindOnPauseTime::class.java).fromJson(json) ?: return RewindOnPauseTime.Default
        parsed.copy(seconds = RewindOnPauseTime.clampedSeconds(parsed.seconds))
      } catch (e: com.squareup.moshi.JsonDataException) {
        Timber.w("Stored rewind on pause time is malformed, resetting due to: ${e.message}")
        store.remove(KEY_REWIND_ON_PAUSE_TIME, commit = true)
        RewindOnPauseTime.Default
      }
    }

    fun saveRewindOnPauseTime(rewindOnPauseTime: RewindOnPauseTime) {
      val json = moshi.adapter(RewindOnPauseTime::class.java).toJson(rewindOnPauseTime)
      store.putString(KEY_REWIND_ON_PAUSE_TIME, json, commit = true)
    }

    fun markBookLastActive(
      bookId: String,
      epochMillis: Long,
    ) {
      synchronized(bookLastActiveLock) {
        val current = bookLastActive.get().toMutableMap()
        current[bookId] = epochMillis

        val trimmed =
          if (current.size > MAX_BOOK_LAST_ACTIVE_ENTRIES) {
            current
              .entries
              .sortedBy { it.value }
              .takeLast(MAX_BOOK_LAST_ACTIVE_ENTRIES)
              .associate { it.toPair() }
          } else {
            current
          }

        try {
          val adapter = moshi.adapter<Map<String, Long>>(bookLastActiveType)
          store.putString(KEY_BOOK_LAST_ACTIVE, adapter.toJson(trimmed))
          bookLastActive.set(trimmed)
        } catch (t: Throwable) {
          Timber.w("Unable to persist last active time for $bookId due to: ${t.message}")
        }
      }
    }

    fun getBookLastActive(bookId: String): Long? = bookLastActive.get()[bookId]

    fun getEqualizer(): EqualizerSettings {
      val json = store.getString(KEY_EQUALIZER) ?: return EqualizerSettings.Default
      return try {
        moshi.adapter(EqualizerSettings::class.java).fromJson(json) ?: EqualizerSettings.Default
      } catch (e: com.squareup.moshi.JsonDataException) {
        Timber.w("Stored equalizer is malformed, resetting due to: ${e.message}")
        store.remove(KEY_EQUALIZER, commit = true)
        EqualizerSettings.Default
      }
    }

    fun saveEqualizer(settings: EqualizerSettings) {
      val json = moshi.adapter(EqualizerSettings::class.java).toJson(settings)
      store.putString(KEY_EQUALIZER, json, commit = true)
    }

    fun getDefaultTimerOption(): TimerOption? {
      val json = store.getString(KEY_DEFAULT_SLEEP_TIMER) ?: return null
      return try {
        moshi.adapter(TimerOptionDto::class.java).fromJson(json)?.toTimerOption()
      } catch (t: Throwable) {
        Timber.w("Unable to read default sleep timer due to: ${t.message}")
        null
      }
    }

    fun saveDefaultTimerOption(option: TimerOption?) {
      when (option) {
        null -> store.remove(KEY_DEFAULT_SLEEP_TIMER)
        else -> store.putString(KEY_DEFAULT_SLEEP_TIMER, moshi.adapter(TimerOptionDto::class.java).toJson(option.toDto()))
      }
    }

    private fun savePlayingItemInternal(
      libraryId: String,
      item: DetailedItem?,
    ) {
      synchronized(playingItemLock) {
        val current = playingItems.get().toMutableMap()

        if (item == null) {
          current.remove(libraryId)
        } else {
          current[libraryId] = item
        }

        try {
          val adapter = moshi.adapter<Map<String, DetailedItem>>(playingItemsType)
          store.putString(KEY_PLAYING_ITEM, adapter.toJson(current))
          playingItems.set(current)
        } catch (t: Throwable) {
          Timber.w("Unable to persist playing item for $libraryId due to: ${t.message}")
        }
      }
    }

    private fun readPlayingItems(): Map<String, DetailedItem> =
      try {
        store
          .getString(KEY_PLAYING_ITEM)
          ?.let { moshi.adapter<Map<String, DetailedItem>>(playingItemsType).fromJson(it) }
          ?: emptyMap()
      } catch (t: Throwable) {
        Timber.w("Unable to read stored playing items, returning empty due to: ${t.message}")
        emptyMap()
      }

    private fun readBookLastActive(): Map<String, Long> =
      try {
        store
          .getString(KEY_BOOK_LAST_ACTIVE)
          ?.let { moshi.adapter<Map<String, Long>>(bookLastActiveType).fromJson(it) }
          ?: emptyMap()
      } catch (t: Throwable) {
        Timber.w("Unable to read stored book last active times, returning empty due to: ${t.message}")
        emptyMap()
      }

    private fun TimerOption.toDto() =
      when (this) {
        CurrentEpisodeTimerOption -> TimerOptionDto(type = "episode")
        is DurationTimerOption -> TimerOptionDto(type = "duration", minutes = duration)
      }

    private fun TimerOptionDto.toTimerOption(): TimerOption? =
      when (type) {
        "episode" -> CurrentEpisodeTimerOption
        "duration" -> minutes?.let { DurationTimerOption(it) }
        else -> null
      }

    companion object {
      private const val KEY_PLAYING_ITEM = "playing_item"
      private const val KEY_VOLUME_BOOST = "volume_boost"
      private const val KEY_PREFERRED_PLAYBACK_SPEED = "preferred_playback_speed"
      private const val KEY_PREFERRED_SEEK_TIME = "preferred_seek_time"
      private const val KEY_SOFTWARE_CODECS = "software_codecs"
      private const val KEY_AUDIO_FOCUS_LOSS_POLICY = "audio_focus_loss_policy"
      private const val KEY_EQUALIZER = "equalizer"
      private const val KEY_DEFAULT_SLEEP_TIMER = "default_sleep_timer"
      private const val KEY_REWIND_ON_PAUSE_TIME = "rewind_on_pause_time"
      private const val KEY_BOOK_LAST_ACTIVE = "book_last_active"
      private const val MAX_BOOK_LAST_ACTIVE_ENTRIES = 100

      private val playingItemsType =
        Types.newParameterizedType(
          Map::class.java,
          String::class.java,
          DetailedItem::class.java,
        )

      private val bookLastActiveType =
        Types.newParameterizedType(
          Map::class.java,
          String::class.java,
          Long::class.javaObjectType,
        )
    }
  }
