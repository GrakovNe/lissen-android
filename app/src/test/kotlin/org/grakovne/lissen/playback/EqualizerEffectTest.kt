package org.grakovne.lissen.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.domain.EqualizerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerEffectTest {
  private val capabilities =
    EqualizerCapabilities.Available(
      bands = listOf(BandInfo(centerFreqHz = 60, upperFreqHz = 120)),
      minDb = -15,
      maxDb = 15,
    )

  private class FakeEffect(
    val sessionId: Int,
  ) : EqualizerEffect {
    val applied = mutableListOf<EqualizerSettings>()
    var closed = false
    var failing = false

    override fun apply(settings: EqualizerSettings) {
      if (failing) throw IllegalStateException("audioserver refused")
      applied += settings
    }

    override fun close() {
      closed = true
    }
  }

  private val sessions = MutableSharedFlow<Int>(replay = 1)
  private val settings = MutableStateFlow(EqualizerSettings.Default)
  private val attached = mutableListOf<FakeEffect>()
  private var probes = 0
  private var probed: EqualizerCapabilities = capabilities

  private fun pipeline(attach: (Int, EqualizerCapabilities.Available) -> EqualizerEffect = ::fakeEffect) =
    equalizerEffects(sessions, ::probe, attach).applying(settings)

  private fun fakeEffect(
    sessionId: Int,
    @Suppress("unused") capabilities: EqualizerCapabilities.Available,
  ): EqualizerEffect = FakeEffect(sessionId).also { attached += it }

  private fun probe(): EqualizerCapabilities = probed.also { probes++ }

  private fun TestScope.session(id: Int) {
    sessions.tryEmit(id)
    advanceUntilIdle()
  }

  private fun TestScope.prefer(vararg gains: Int) {
    settings.value = EqualizerSettings(gains = gains.toList())
    advanceUntilIdle()
  }

  @Test
  fun `attaches an effect to the session and applies the current settings`() =
    runTest {
      val job = launch { pipeline().collect() }
      session(7)

      assertEquals(listOf(7), attached.map { it.sessionId })
      assertEquals(listOf(EqualizerSettings.Default), attached.single().applied)
      job.cancel()
    }

  @Test
  fun `applies every settings change to the attached effect`() =
    runTest {
      val job = launch { pipeline().collect() }
      session(7)
      prefer(3)
      prefer(-2)

      assertEquals(listOf(emptyList(), listOf(3), listOf(-2)), attached.single().applied.map { it.gains })
      job.cancel()
    }

  @Test
  fun `closes the effect of a replaced session and applies the latest settings to the new one`() =
    runTest {
      val job = launch { pipeline().collect() }
      session(7)
      prefer(5)
      session(8)

      assertEquals(listOf(7, 8), attached.map { it.sessionId })
      assertTrue(attached[0].closed)
      assertFalse(attached[1].closed)
      assertEquals(listOf(listOf(5)), attached[1].applied.map { it.gains })
      job.cancel()
    }

  @Test
  fun `closes the effect when the pipeline stops`() =
    runTest {
      val job = launch { pipeline().collect() }
      session(7)
      job.cancel()
      advanceUntilIdle()

      assertTrue(attached.single().closed)
    }

  @Test
  fun `keeps the effect when the same session is reported again`() =
    runTest {
      val job = launch { pipeline().collect() }
      session(7)
      session(7)

      assertEquals(1, attached.size)
      assertFalse(attached.single().closed)
      job.cancel()
    }

  @Test
  fun `attaches nothing to an unset session`() =
    runTest {
      val job = launch { pipeline().collect() }
      session(0)

      assertTrue(attached.isEmpty())
      assertEquals(0, probes)
      job.cancel()
    }

  @Test
  fun `attaches nothing while capabilities are unavailable and probes again for the next session`() =
    runTest {
      probed = EqualizerCapabilities.Unavailable
      val job = launch { pipeline().collect() }
      session(7)
      assertTrue(attached.isEmpty())

      probed = capabilities
      session(8)

      assertEquals(listOf(8), attached.map { it.sessionId })
      assertEquals(2, probes)
      job.cancel()
    }

  @Test
  fun `survives an effect that cannot be built`() =
    runTest {
      var attempts = 0
      val job =
        launch {
          pipeline { id, _ ->
            if (attempts++ == 0) throw UnsupportedOperationException("no effect")
            FakeEffect(id).also { attached += it }
          }.collect()
        }
      session(7)
      session(8)

      assertEquals(listOf(8), attached.map { it.sessionId })
      job.cancel()
    }

  @Test
  fun `survives an apply that fails and keeps applying later changes`() =
    runTest {
      val job = launch { pipeline().collect() }
      session(7)

      val effect = attached.single()
      effect.failing = true
      prefer(1)
      effect.failing = false
      prefer(2)

      assertEquals(listOf(emptyList(), listOf(2)), effect.applied.map { it.gains })
      job.cancel()
    }
}
