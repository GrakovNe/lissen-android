package org.grakovne.lissen.playback

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.mockk
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.SecurePreferenceStore
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.playback.autoskip.AutoSkipPreferences
import org.grakovne.lissen.playback.autoskip.AutoSkipService
import org.grakovne.lissen.playback.autoskip.PlaybackSteps
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The playback services on a real ExoPlayer playing [item] four times over, recording every
 * discontinuity: the timer and the auto-skip always, whatever else a subclass attaches. A
 * scenario seeks in the same main-thread task as the check that playback is where it needs to be.
 */
@OptIn(UnstableApi::class)
abstract class RealPlayerTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  protected val context: Context = instrumentation.targetContext

  protected open val configuration = AutoSkipConfiguration(introSeconds = 2, outroSeconds = 2)
  protected val steps = PlaybackSteps()
  protected val synchronization = mockk<PlaybackSynchronizationService>(relaxed = true)
  protected val discontinuities = CopyOnWriteArrayList<Discontinuity>()

  protected val syncState = SyncStateStore()
  protected val autoSkipPreferences = AutoSkipPreferences(SecurePreferenceStore(context))

  protected lateinit var player: ExoPlayer
  protected lateinit var timer: PlaybackTimer

  // an id per test: the services of the earlier tests are never stopped, so they stay quiet on another item
  protected abstract val item: DetailedItem

  protected abstract val mediaSourceFactory: MediaSource.Factory

  // called before the player is built, so the media it reads can be made ready
  protected abstract fun prepareQueue(): List<MediaItem>

  // called on the main thread once the player and its timer exist, before playback starts
  protected open fun attachServices() {}

  @Before
  fun startPlayback() {
    autoSkipPreferences.save(item.id, configuration)
    val queue = prepareQueue()

    onMain {
      player = ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build()
      player.addListener(
        object : Player.Listener {
          override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
          ) {
            discontinuities +=
              Discontinuity(reason, oldPosition.mediaItemIndex, oldPosition.positionMs, newPosition.mediaItemIndex, newPosition.positionMs)
          }
        },
      )
      timer = PlaybackTimer(PlaybackEventBus(), player)
      AutoSkipService(player, autoSkipPreferences, syncState, timer, synchronization, steps).onCreate()
      attachServices()

      // in the order the playback service does it
      syncState.update { it.start(item) }
      player.setMediaItems(queue)
      player.setPlaybackSpeed(SPEED)
      player.prepare()
      player.play()
    }
  }

  @After
  fun releasePlayer() {
    onMain {
      timer.stopTimer()
      player.release()
    }
    autoSkipPreferences.save(item.id, AutoSkipConfiguration.disabled)
  }

  protected fun remainingInChapter(): Double =
    PlaybackGeometry.remainingInChapter(
      item,
      totalPosition = PlaybackGeometry.totalPosition(item, player.currentMediaItemIndex, player.currentPosition / 1_000.0),
      speed = SPEED,
      autoSkip = configuration,
    )!!

  /** Runs [action] once playback is in chapter [index] within [positions]; returns false to keep waiting. */
  protected fun inChapter(
    index: Int,
    positions: LongRange,
    action: () -> Unit,
  ): Boolean {
    if (player.currentMediaItemIndex != index || !player.isPlaying || player.currentPosition !in positions) return false

    action()
    return true
  }

  /** Runs [action] once playback is in [index] well before its outro; returns false to keep waiting. */
  protected fun inChapterBeforeOutro(
    index: Int,
    action: () -> Unit,
  ): Boolean {
    val outroStartMs = (item.chapters[index].duration - configuration.outroSeconds) * 1_000L
    if (player.currentMediaItemIndex != index || !player.isPlaying) return false
    if (player.currentPosition >= outroStartMs - 2_000L) {
      fail("chapter $index was already at ${player.currentPosition}ms, too close to its outro at ${outroStartMs}ms")
    }

    action()
    return true
  }

  protected fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

  protected fun awaitOnMain(
    what: String,
    timeoutMs: Long = 20_000L,
    condition: () -> Boolean,
  ) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
      var met = false
      onMain { met = condition() }
      if (met) return
      Thread.sleep(50L)
    }
    fail("timed out waiting for $what; discontinuities so far: $discontinuities")
  }

  protected companion object {
    const val SPEED = 4f

    fun item(
      id: String,
      chapterSeconds: List<Int>,
      files: List<BookFile> = emptyList(),
    ): DetailedItem {
      var start = 0.0
      val chapters =
        chapterSeconds.mapIndexed { index, seconds ->
          PlayingChapter(
            available = true,
            podcastEpisodeState = null,
            duration = seconds.toDouble(),
            start = start,
            end = start + seconds,
            title = "Chapter $index",
            id = "c$index",
            index = index,
          ).also { start += seconds }
        }

      return DetailedItem(
        id = id,
        title = "Silence",
        subtitle = null,
        author = null,
        narrator = null,
        publisher = null,
        series = emptyList(),
        year = null,
        abstract = null,
        files = files,
        chapters = chapters,
        progress = null,
        libraryId = "lib",
        localProvided = files.isNotEmpty(),
        createdAt = 0L,
        updatedAt = 0L,
      )
    }
  }
}

/** Plays silence as long as the media id says: "silence:<milliseconds>". */
@OptIn(UnstableApi::class)
class SilenceFactory : MediaSource.Factory {
  override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider) = this

  override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy) = this

  override fun getSupportedTypes() = intArrayOf(C.CONTENT_TYPE_OTHER)

  override fun createMediaSource(mediaItem: MediaItem): MediaSource =
    SilenceMediaSource(mediaItem.mediaId.removePrefix("silence:").toLong() * 1_000L)
}

data class Discontinuity(
  val reason: Int,
  val fromIndex: Int,
  val fromMs: Long,
  val toIndex: Int,
  val toMs: Long,
) {
  fun isSeek(
    from: Int,
    to: Pair<Int, Long>,
  ) = reason == Player.DISCONTINUITY_REASON_SEEK && fromIndex == from && toIndex == to.first && toMs == to.second
}
