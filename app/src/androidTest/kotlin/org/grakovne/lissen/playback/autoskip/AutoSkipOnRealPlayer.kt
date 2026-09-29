package org.grakovne.lissen.playback.autoskip

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.mockk
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.SecurePreferenceStore
import org.grakovne.lissen.playback.PlaybackEventBus
import org.grakovne.lissen.playback.PlaybackGeometry
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The auto-skip service on a real ExoPlayer, playing [item] from its first chapter, fourfold, with
 * [configuration] skipped at both ends; every discontinuity the player reports is recorded. The
 * synchronization is a mock that only counts the chapter ends. Every seek a scenario makes is made
 * in the same main-thread task as the check that playback is where the scenario needs it.
 */
@OptIn(UnstableApi::class)
abstract class AutoSkipOnRealPlayer {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  protected val context: Context = instrumentation.targetContext

  protected val configuration = AutoSkipConfiguration(introSeconds = 2, outroSeconds = 2)
  protected val steps = PlaybackSteps()
  protected val synchronization = mockk<PlaybackSynchronizationService>(relaxed = true)
  protected val discontinuities = CopyOnWriteArrayList<Discontinuity>()

  private val syncState = SyncStateStore()
  private val preferences = AutoSkipPreferences(SecurePreferenceStore(context))

  protected lateinit var player: ExoPlayer
  protected lateinit var timer: PlaybackTimer

  /** Its own id per test: the services of the earlier tests, which nothing stops, keep their plan and stay quiet. */
  protected abstract val item: DetailedItem

  protected abstract val mediaSourceFactory: MediaSource.Factory

  /** The queue of [item], with whatever it reads from made ready; called before the player is built. */
  protected abstract fun prepareQueue(): List<MediaItem>

  @Before
  fun startPlayback() {
    preferences.save(item.id, configuration)
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
      AutoSkipService(player, preferences, syncState, timer, synchronization, steps).onCreate()

      // the way the playback service does it: the item is known to the synchronization, then the queue is set
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
    preferences.save(item.id, AutoSkipConfiguration.disabled)
  }

  /** What an episode timer armed right now would count, on the main thread. */
  protected fun remainingInChapter(): Double =
    PlaybackGeometry.remainingInChapter(
      item,
      totalPosition = PlaybackGeometry.totalPosition(item, player.currentMediaItemIndex, player.currentPosition / 1_000.0),
      speed = SPEED,
      autoSkip = configuration,
    )!!

  /** True, with [action] done, once playback runs in [index] and is still well before its outro; false to keep waiting. */
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

    /** An item of back-to-back chapters of these lengths, over [files]. */
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
