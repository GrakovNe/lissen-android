package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.grakovne.lissen.playback.cast.CastDevice
import org.grakovne.lissen.playback.cast.CastManager
import org.grakovne.lissen.playback.cast.CastSession
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class CastViewModel
  @Inject
  constructor(
    private val castManager: CastManager,
  ) : ViewModel() {
    val devices: StateFlow<List<CastDevice>> = castManager.devices
    val discovering: StateFlow<Boolean> = castManager.discovering
    val session: StateFlow<CastSession?> = castManager.session

    fun startDiscovery() = castManager.startDiscovery()

    fun stopDiscovery() = castManager.stopDiscovery()

    fun connect(device: CastDevice) {
      Timber.d("User action: cast to ${device.name}")
      castManager.connect(device)
    }

    fun disconnect() {
      Timber.d("User action: stop casting")
      castManager.disconnect()
    }

    fun dismissFailure() = castManager.dismissFailure()
  }
