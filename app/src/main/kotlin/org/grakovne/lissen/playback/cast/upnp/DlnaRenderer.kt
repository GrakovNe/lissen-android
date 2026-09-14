package org.grakovne.lissen.playback.cast.upnp

import android.os.SystemClock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.playback.cast.CastDevice
import org.grakovne.lissen.playback.cast.CastMedia
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.model.ModelUtil
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.message.header.UDADeviceTypeHeader
import org.jupnp.model.meta.Service
import org.jupnp.model.types.UDN
import org.jupnp.support.avtransport.callback.GetPositionInfo
import org.jupnp.support.avtransport.callback.GetTransportInfo
import org.jupnp.support.avtransport.callback.Pause
import org.jupnp.support.avtransport.callback.Play
import org.jupnp.support.avtransport.callback.Seek
import org.jupnp.support.avtransport.callback.SetAVTransportURI
import org.jupnp.support.avtransport.callback.Stop
import org.jupnp.support.contentdirectory.DIDLParser
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.DIDLObject
import org.jupnp.support.model.PositionInfo
import org.jupnp.support.model.ProtocolInfo
import org.jupnp.support.model.Res
import org.jupnp.support.model.TransportInfo
import org.jupnp.support.model.TransportState
import org.jupnp.support.model.item.MusicTrack
import org.jupnp.support.renderingcontrol.callback.SetVolume
import org.jupnp.util.MimeType
import timber.log.Timber
import java.io.IOException
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class RendererTransport {
  IDLE,
  BUFFERING,
  PLAYING,
  PAUSED,
  STOPPED,
  ENDED,
  ERROR,
}

data class RendererStatus(
  val transport: RendererTransport = RendererTransport.IDLE,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val mediaUrl: String? = null,
  val error: String? = null,
  val updatedAt: Long = SystemClock.elapsedRealtime(),
)

/**
 * UPnP AVTransport control point on top of jUPnP. Transport state and position are
 * polled because most renderers only event state changes, not the playing time.
 */
class DlnaRenderer(
  val device: CastDevice,
  private val upnpStack: UpnpStack,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val commandLock = Mutex()

  private val _status = MutableStateFlow(RendererStatus())
  val status: StateFlow<RendererStatus> = _status.asStateFlow()

  private var pollJob: Job? = null
  private var avTransport: Service<*, *>? = null
  private var renderingControl: Service<*, *>? = null
  private var leased = false

  @Volatile
  private var loadedUrl: String? = null

  @Volatile
  private var pendingSeekMs: Long? = null

  @Volatile
  private var stopRequested = false

  @Volatile
  private var startedAt = 0L

  @Volatile
  private var seenActive = false

  suspend fun connect() {
    val upnp = upnpStack.acquire()
    leased = true

    val udn = UDN(device.udn)
    var remote = upnp.registry.getDevice(udn, false)

    if (remote == null) {
      // A freshly started stack has an empty registry: ask the network and wait for the answer.
      upnp.controlPoint.search(UDADeviceTypeHeader(UpnpRendererDiscovery.MEDIA_RENDERER))
      val deadline = SystemClock.elapsedRealtime() + DISCOVERY_WAIT_MS
      while (remote == null && SystemClock.elapsedRealtime() < deadline) {
        delay(DISCOVERY_POLL_MS)
        remote = upnp.registry.getDevice(udn, false)
      }
    }

    if (remote == null) throw IOException("Renderer ${device.name} is no longer on the network")

    avTransport = remote.findService(UpnpRendererDiscovery.AV_TRANSPORT) ?: throw IOException("Renderer has no AVTransport service")
    renderingControl = remote.findService(UpnpRendererDiscovery.RENDERING_CONTROL)

    transportState()
    startPolling()
  }

  suspend fun load(
    media: CastMedia,
    startPositionMs: Long,
    autoPlay: Boolean,
  ) {
    commandLock.withLock {
      stopRequested = false
      pendingSeekMs = startPositionMs.takeIf { it > 0 }

      execute { c ->
        object : SetAVTransportURI(transport(), media.url, buildMetadata(media)) {
          override fun success(invocation: ActionInvocation<*>) = c.resume(Unit)

          override fun failure(
            invocation: ActionInvocation<*>,
            operation: UpnpResponse?,
            defaultMsg: String,
          ) = c.fail(defaultMsg)
        }
      }
      loadedUrl = media.url
      startedAt = SystemClock.elapsedRealtime()
      seenActive = false

      _status.value =
        RendererStatus(
          transport = if (autoPlay) RendererTransport.BUFFERING else RendererTransport.PAUSED,
          positionMs = startPositionMs,
          durationMs = media.durationMs,
          mediaUrl = media.url,
        )

      if (autoPlay) {
        execute { c ->
          object : Play(transport()) {
            override fun success(invocation: ActionInvocation<*>) = c.resume(Unit)

            override fun failure(
              invocation: ActionInvocation<*>,
              operation: UpnpResponse?,
              defaultMsg: String,
            ) = c.fail(defaultMsg)
          }
        }
        applyPendingSeek()
      }
    }
  }

  suspend fun play() {
    commandLock.withLock {
      stopRequested = false
      startedAt = SystemClock.elapsedRealtime()
      seenActive = false
      execute { c ->
        object : Play(transport()) {
          override fun success(invocation: ActionInvocation<*>) = c.resume(Unit)

          override fun failure(
            invocation: ActionInvocation<*>,
            operation: UpnpResponse?,
            defaultMsg: String,
          ) = c.fail(defaultMsg)
        }
      }
      applyPendingSeek()
      _status.value = _status.value.copy(transport = RendererTransport.PLAYING)
    }
  }

  suspend fun pause() {
    commandLock.withLock {
      execute { c ->
        object : Pause(transport()) {
          override fun success(invocation: ActionInvocation<*>) = c.resume(Unit)

          override fun failure(
            invocation: ActionInvocation<*>,
            operation: UpnpResponse?,
            defaultMsg: String,
          ) = c.fail(defaultMsg)
        }
      }
      _status.value = _status.value.copy(transport = RendererTransport.PAUSED)
    }
  }

  suspend fun seekTo(positionMs: Long) {
    commandLock.withLock {
      val transport = _status.value.transport
      if (transport == RendererTransport.PLAYING || transport == RendererTransport.PAUSED) {
        if (!trySeek(positionMs)) pendingSeekMs = positionMs
      } else {
        pendingSeekMs = positionMs
      }
      _status.value = _status.value.copy(positionMs = positionMs)
    }
  }

  suspend fun stop() {
    commandLock.withLock {
      stopRequested = true
      runCatching {
        execute { c ->
          object : Stop(transport()) {
            override fun success(invocation: ActionInvocation<*>) = c.resume(Unit)

            override fun failure(
              invocation: ActionInvocation<*>,
              operation: UpnpResponse?,
              defaultMsg: String,
            ) = c.fail(defaultMsg)
          }
        }
      }
      _status.value = _status.value.copy(transport = RendererTransport.STOPPED)
    }
  }

  suspend fun setVolume(volume: Float) {
    val service = renderingControl ?: return
    val level = (volume.coerceIn(0f, 1f) * 100).toLong()
    runCatching {
      execute { c ->
        object : SetVolume(service, level) {
          override fun success(invocation: ActionInvocation<*>) = c.resume(Unit)

          override fun failure(
            invocation: ActionInvocation<*>,
            operation: UpnpResponse?,
            defaultMsg: String,
          ) = c.fail(defaultMsg)
        }
      }
    }.onFailure { Timber.w(it, "SetVolume failed on ${device.name}") }
  }

  fun disconnect() {
    pollJob?.cancel()
    pollJob = null
    scope.cancel()
    if (leased) {
      leased = false
      upnpStack.release()
    }
  }

  private fun transport(): Service<*, *> = avTransport ?: throw IOException("Renderer is not connected")

  private suspend fun applyPendingSeek() {
    val target = pendingSeekMs ?: return

    repeat(SEEK_ATTEMPTS) {
      val state = runCatching { transportState() }.getOrNull()
      if (state == TransportState.PLAYING || state == TransportState.PAUSED_PLAYBACK) {
        if (trySeek(target)) {
          pendingSeekMs = null
          return
        }
      }
      delay(SEEK_RETRY_DELAY_MS)
    }

    Timber.w("Renderer ${device.name} did not accept seek to $target ms")
    pendingSeekMs = null
  }

  private suspend fun trySeek(positionMs: Long): Boolean =
    runCatching {
      execute { c ->
        object : Seek(transport(), ModelUtil.toTimeString(positionMs / 1000)) {
          override fun success(invocation: ActionInvocation<*>) = c.resume(Unit)

          override fun failure(
            invocation: ActionInvocation<*>,
            operation: UpnpResponse?,
            defaultMsg: String,
          ) = c.fail(defaultMsg)
        }
      }
      true
    }.onFailure { Timber.d("Seek rejected by ${device.name}: ${it.message}") }
      .getOrDefault(false)

  private fun startPolling() {
    pollJob?.cancel()
    pollJob =
      scope.launch {
        while (isActive) {
          runCatching { poll() }.onFailure { Timber.d("Renderer poll failed: ${it.message}") }
          delay(POLL_INTERVAL_MS)
        }
      }
  }

  private suspend fun poll() {
    val expectedUrl = loadedUrl ?: return

    val state = transportState()
    val position = positionInfo()

    val current = _status.value
    val trackMatches = position.trackURI.isNullOrBlank() || position.trackURI == expectedUrl
    val active = state == TransportState.PLAYING || state == TransportState.PAUSED_PLAYBACK || state == TransportState.TRANSITIONING
    if (active && trackMatches) seenActive = true

    // Right after SetAVTransportURI/Play a renderer keeps reporting the previous track
    // (often STOPPED at its end) until it has actually started the new one.
    val settling = !seenActive && SystemClock.elapsedRealtime() - startedAt < SETTLE_GRACE_MS
    val stale = !trackMatches || (settling && !active)

    if (stale && !stopRequested) {
      if (current.transport == RendererTransport.BUFFERING || current.transport == RendererTransport.PAUSED) return
      _status.value = current.copy(transport = RendererTransport.BUFFERING, updatedAt = SystemClock.elapsedRealtime())
      return
    }

    val positionMs = position.trackElapsedSeconds * 1000
    val durationMs = position.trackDurationSeconds * 1000

    val transport =
      when (state) {
        TransportState.PLAYING -> {
          RendererTransport.PLAYING
        }

        TransportState.PAUSED_PLAYBACK, TransportState.PAUSED_RECORDING -> {
          RendererTransport.PAUSED
        }

        TransportState.TRANSITIONING -> {
          RendererTransport.BUFFERING
        }

        TransportState.STOPPED, TransportState.NO_MEDIA_PRESENT -> {
          when {
            pendingSeekMs != null && !stopRequested -> RendererTransport.PAUSED

            stopRequested -> RendererTransport.STOPPED

            // A track that was never seen playing cannot have ended: some renderers report
            // RelTime equal to the duration for a freshly loaded, still stopped track.
            !seenActive -> RendererTransport.PAUSED

            durationMs > 0 && positionMs >= durationMs - END_TOLERANCE_MS -> RendererTransport.ENDED

            current.durationMs > 0 && current.positionMs >= current.durationMs - END_TOLERANCE_MS -> RendererTransport.ENDED

            else -> RendererTransport.STOPPED
          }
        }

        else -> {
          current.transport
        }
      }

    val reportedPosition =
      when {
        pendingSeekMs != null && transport != RendererTransport.PLAYING -> pendingSeekMs ?: positionMs
        !seenActive -> current.positionMs
        else -> positionMs
      }

    _status.value =
      current.copy(
        transport = transport,
        positionMs = reportedPosition,
        durationMs = durationMs.takeIf { it > 0 } ?: current.durationMs,
        mediaUrl = expectedUrl,
        updatedAt = SystemClock.elapsedRealtime(),
      )
  }

  private suspend fun transportState(): TransportState =
    suspendCancellableCoroutine { continuation ->
      val callback =
        object : GetTransportInfo(transport()) {
          override fun received(
            invocation: ActionInvocation<*>,
            transportInfo: TransportInfo,
          ) = continuation.resume(transportInfo.currentTransportState)

          override fun failure(
            invocation: ActionInvocation<*>,
            operation: UpnpResponse?,
            defaultMsg: String,
          ) = continuation.resumeWithException(IOException(defaultMsg))
        }
      upnpStack.current?.controlPoint?.execute(callback)
        ?: continuation.resumeWithException(IOException("UPnP stack is not running"))
    }

  private suspend fun positionInfo(): PositionInfo =
    suspendCancellableCoroutine { continuation ->
      val callback =
        object : GetPositionInfo(transport()) {
          override fun received(
            invocation: ActionInvocation<*>,
            positionInfo: PositionInfo,
          ) = continuation.resume(positionInfo)

          override fun failure(
            invocation: ActionInvocation<*>,
            operation: UpnpResponse?,
            defaultMsg: String,
          ) = continuation.resumeWithException(IOException(defaultMsg))
        }
      upnpStack.current?.controlPoint?.execute(callback)
        ?: continuation.resumeWithException(IOException("UPnP stack is not running"))
    }

  /** Sends an action and suspends until the renderer answers. */
  private suspend fun execute(build: (CancellableContinuation<Unit>) -> ActionCallback) {
    val controlPoint = upnpStack.current?.controlPoint ?: throw IOException("UPnP stack is not running")
    suspendCancellableCoroutine { continuation -> controlPoint.execute(build(continuation)) }
  }

  private fun CancellableContinuation<Unit>.fail(message: String) = resumeWithException(IOException(message))

  companion object {
    private const val POLL_INTERVAL_MS = 1_000L
    private const val SEEK_ATTEMPTS = 8
    private const val SEEK_RETRY_DELAY_MS = 400L
    private const val END_TOLERANCE_MS = 2_500L
    private const val SETTLE_GRACE_MS = 6_000L
    private const val DISCOVERY_WAIT_MS = 8_000L
    private const val DISCOVERY_POLL_MS = 250L

    internal fun buildMetadata(media: CastMedia): String {
      val resource =
        Res(ProtocolInfo(MimeType.valueOf(media.mimeType)), null, media.url).apply {
          if (media.durationMs > 0) duration = ModelUtil.toTimeString(media.durationMs / 1000)
        }

      val track = MusicTrack("lissen-0", "-1", media.title, media.subtitle, media.subtitle, media.subtitle, resource)
      media.artworkUrl?.let { runCatching { track.addProperty(DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(it))) } }

      return runCatching { DIDLParser().generate(DIDLContent().addItem(track)) }
        .onFailure { Timber.w(it, "Unable to build DIDL metadata") }
        .getOrDefault("")
    }
  }
}
