package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.grakovne.lissen.domain.EqualizerSettings
import timber.log.Timber

interface EqualizerEffect : AutoCloseable {
  fun apply(settings: EqualizerSettings)
}

// One effect per audio session: it is built when a session appears and closed when the session is
// replaced, so no effect outlives the audio it shapes. A session without an effect yields null.
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
fun equalizerEffects(
  sessionIds: Flow<Int>,
  capabilities: suspend () -> EqualizerCapabilities,
  attach: (sessionId: Int, capabilities: EqualizerCapabilities.Available) -> EqualizerEffect,
): Flow<EqualizerEffect?> =
  sessionIds
    .distinctUntilChanged()
    .flatMapLatest { sessionId ->
      flow {
        val effect = buildEffect(sessionId, capabilities, attach)

        if (effect == null) {
          emit(null)
          return@flow
        }

        try {
          emit(effect)
          awaitCancellation()
        } finally {
          effect.close()
        }
      }
    }

fun Flow<EqualizerEffect?>.applying(settings: Flow<EqualizerSettings>): Flow<Unit> =
  combine(settings) { effect, current ->
    effect ?: return@combine

    try {
      effect.apply(current)
    } catch (ex: Exception) {
      Timber.e("Unable to apply equalizer due to: $ex")
    }
  }

@OptIn(UnstableApi::class)
private suspend fun buildEffect(
  sessionId: Int,
  capabilities: suspend () -> EqualizerCapabilities,
  attach: (Int, EqualizerCapabilities.Available) -> EqualizerEffect,
): EqualizerEffect? {
  if (sessionId == C.AUDIO_SESSION_ID_UNSET) return null

  return when (val available = capabilities()) {
    EqualizerCapabilities.Unavailable -> {
      Timber.w("Equalizer is unavailable on this device, audio session $sessionId plays unshaped")
      null
    }

    is EqualizerCapabilities.Available -> {
      try {
        attach(sessionId, available).also {
          Timber.d("Equalizer attached to audio session $sessionId with ${available.bands.size} bands")
        }
      } catch (ex: Exception) {
        Timber.e("Unable to attach equalizer due to ${ex.message}")
        null
      }
    }
  }
}
