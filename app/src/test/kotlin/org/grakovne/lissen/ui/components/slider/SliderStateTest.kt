package org.grakovne.lissen.ui.components.slider

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The state behind every settings slider (speed, seek, volume boost, equalizer). Animations
 * need a [BroadcastFrameClock] in the context, so [runWithFrames] drives one by hand until the
 * block finishes; `snapTo` needs no frames at all.
 */
class SliderStateTest {
  private object SaverScopeAllowAll : SaverScope {
    override fun canBeSaved(value: Any) = true
  }

  private fun state(
    current: Int,
    bounds: ClosedRange<Int>,
    onUpdate: (Float) -> Unit = {},
  ) = SliderState(current, bounds, onUpdate)

  private fun runWithFrames(
    timeoutMs: Long = 10_000,
    block: suspend () -> Unit,
  ) {
    val clock = BroadcastFrameClock()
    runBlocking {
      val job = launch { withContext(clock) { block() } }
      val deadline = System.currentTimeMillis() + timeoutMs
      var frameNanos = 0L
      while (job.isActive && System.currentTimeMillis() < deadline) {
        clock.sendFrame(frameNanos)
        frameNanos += 50_000_000L
        delay(2)
      }
      withTimeout(timeoutMs) { job.join() }
    }
  }

  @Test
  fun `snapTo clamps above the upper bound and reports the clamped value`() {
    val updates = mutableListOf<Float>()
    val state = state(current = 0, bounds = 0..10, onUpdate = { updates += it })

    runBlocking { state.snapTo(42f) }

    assertEquals(10f, state.current)
    assertEquals(listOf(10f), updates)
  }

  @Test
  fun `snapTo clamps below the lower bound and reports the clamped value`() {
    val updates = mutableListOf<Float>()
    val state = state(current = 5, bounds = 2..8, onUpdate = { updates += it })

    runBlocking { state.snapTo(-100f) }

    assertEquals(2f, state.current)
    assertEquals(listOf(2f), updates)
  }

  @Test
  fun `snapTo inside the bounds passes the value through`() {
    val updates = mutableListOf<Float>()
    val state = state(current = 0, bounds = 0..10, onUpdate = { updates += it })

    runBlocking { state.snapTo(3.5f) }

    assertEquals(3.5f, state.current)
    assertEquals(listOf(3.5f), updates)
  }

  @Test
  fun `snapToNearest rounds down to the nearest integer`() =
    runWithFrames {
      val updates = mutableListOf<Float>()
      val state = state(current = 0, bounds = 0..10, onUpdate = { updates += it })
      state.snapTo(6.4f)
      updates.clear()

      state.snapToNearest()

      assertEquals(6f, state.current)
      assertEquals(listOf(6f), updates)
    }

  @Test
  fun `snapToNearest rounds halves up`() =
    runWithFrames {
      val state = state(current = 0, bounds = 0..10)
      state.snapTo(7.5f)

      state.snapToNearest()

      assertEquals(8f, state.current)
    }

  // the coerceIn inside snapToNearest is unreachable through the public API (snapTo already
  // clamps), so this covers the rounding it performs on a value already inside the bounds
  @Test
  fun `snapToNearest rounds a dragged value to the nearest whole number`() =
    runWithFrames {
      val updates = mutableListOf<Float>()
      val state = state(current = 0, bounds = 0..10, onUpdate = { updates += it })
      state.snapTo(9.8f)

      state.snapToNearest()

      assertEquals(10f, state.current)
      assertEquals(10f, updates.last())
    }

  @Test
  fun `animateDecayTo clamps a target past the upper bound`() =
    runWithFrames {
      val state = state(current = 3, bounds = 0..10)

      state.animateDecayTo(99f)

      assertEquals(10f, state.current)
    }

  @Test
  fun `animateDecayTo clamps a target below the lower bound`() =
    runWithFrames {
      val state = state(current = 3, bounds = 0..10)

      state.animateDecayTo(-40f)

      assertEquals(0f, state.current)
    }

  @Test
  fun `saver round trips the current value and the bounds`() {
    val restored = mutableListOf<Float>()
    val saver = SliderState.saver(onUpdate = { restored += it })

    val dragging = state(current = 7, bounds = 3..12)
    runBlocking { dragging.snapTo(9.4f) }

    val saved = with(saver) { SaverScopeAllowAll.save(dragging) }
    val revived = saver.restore(saved!!)!!

    assertEquals(9f, revived.current)
    assertEquals(3..12, revived.bounds)

    runBlocking { revived.snapTo(5f) }
    assertEquals(listOf(5f), restored)
  }

  @Test
  fun `animateDecayTo lands on a target inside the bounds`() =
    runWithFrames {
      val state = state(current = 2, bounds = 0..10)

      state.animateDecayTo(8f)

      assertEquals(8f, state.current)
    }
}
