package org.grakovne.lissen.playback.cast.upnp

import org.grakovne.lissen.playback.cast.CastDevice
import org.jupnp.UpnpService
import org.jupnp.model.message.header.UDADeviceTypeHeader
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.types.UDADeviceType
import org.jupnp.model.types.UDAServiceType
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks UPnP MediaRenderers through the jUPnP registry. jUPnP handles SSDP, description
 * fetching and expiry; this class only maps hydrated devices with an AVTransport service
 * to the app's [CastDevice] model.
 */
class UpnpRendererDiscovery(
  private val onDevicesChanged: (Map<String, CastDevice>) -> Unit,
) {
  private val devices = ConcurrentHashMap<String, CastDevice>()
  private var attached: UpnpService? = null

  private val listener =
    object : DefaultRegistryListener() {
      override fun remoteDeviceAdded(
        registry: Registry,
        device: RemoteDevice,
      ) = consider(device)

      override fun remoteDeviceUpdated(
        registry: Registry,
        device: RemoteDevice,
      ) = consider(device)

      override fun remoteDeviceRemoved(
        registry: Registry,
        device: RemoteDevice,
      ) {
        val id = "dlna:${device.identity.udn.identifierString}"
        if (devices.remove(id) != null) onDevicesChanged(devices.toMap())
      }

      override fun remoteDeviceDiscoveryFailed(
        registry: Registry,
        device: RemoteDevice,
        e: Exception?,
      ) {
        Timber.w("UPnP device description failed for ${device.identity.descriptorURL}: ${e?.message}")
      }
    }

  fun attach(service: UpnpService) {
    if (attached === service) return
    attached = service
    service.registry.addListener(listener)
    service.registry.remoteDevices.forEach(::consider)
    search()
  }

  fun search() {
    attached?.controlPoint?.search(UDADeviceTypeHeader(MEDIA_RENDERER))
  }

  fun detach() {
    attached?.registry?.removeListener(listener)
    attached = null
    devices.clear()
    onDevicesChanged(emptyMap())
  }

  private fun consider(device: RemoteDevice) {
    val mapped = toCastDevice(device) ?: return
    devices[mapped.id] = mapped
    onDevicesChanged(devices.toMap())
  }

  companion object {
    val MEDIA_RENDERER = UDADeviceType("MediaRenderer")
    val AV_TRANSPORT = UDAServiceType("AVTransport")
    val RENDERING_CONTROL = UDAServiceType("RenderingControl")

    fun toCastDevice(device: RemoteDevice): CastDevice? {
      if (!device.isFullyHydrated) return null

      val renderer =
        device.findDevices(MEDIA_RENDERER)?.firstOrNull { it.findService(AV_TRANSPORT) != null }
          ?: device.takeIf { it.findService(AV_TRANSPORT) != null }
          ?: return null

      val udn = renderer.identity.udn.identifierString
      val host = device.identity.descriptorURL?.host ?: return null

      return CastDevice(
        id = "dlna:$udn",
        udn = udn,
        name = renderer.details?.friendlyName?.takeIf { it.isNotBlank() } ?: host,
        host = host,
        model =
          renderer.details
            ?.modelDetails
            ?.modelName
            ?.takeIf { it.isNotBlank() },
      )
    }
  }
}
