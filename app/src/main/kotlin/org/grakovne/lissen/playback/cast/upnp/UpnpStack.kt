package org.grakovne.lissen.playback.cast.upnp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceImpl
import org.jupnp.android.AndroidRouter
import org.jupnp.android.AndroidUpnpServiceConfiguration
import org.jupnp.protocol.ProtocolFactory
import org.jupnp.registry.Registry
import org.jupnp.transport.Router
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the jUPnP stack for the process. The stack is expensive (Jetty server, multicast
 * listener, registry maintenance), so it is started on the first lease and shut down a
 * few seconds after the last lease is released: discovery holds one while the picker is
 * open, a DLNA session holds one while casting.
 */
@Singleton
class UpnpStack
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    private var service: UpnpService? = null
    private var leases = 0
    private var pendingShutdown: Thread? = null

    val current: UpnpService?
      get() = synchronized(this) { service }

    suspend fun acquire(): UpnpService =
      withContext(Dispatchers.IO) {
        synchronized(this@UpnpStack) {
          leases++
          pendingShutdown?.interrupt()
          pendingShutdown = null
          service ?: start().also { service = it }
        }
      }

    fun release() {
      synchronized(this) {
        leases = (leases - 1).coerceAtLeast(0)
        if (leases > 0 || service == null || pendingShutdown != null) return

        pendingShutdown =
          Thread(
            {
              try {
                Thread.sleep(SHUTDOWN_GRACE_MS)
              } catch (_: InterruptedException) {
                return@Thread
              }

              val toStop =
                synchronized(this) {
                  if (leases > 0) return@Thread
                  pendingShutdown = null
                  service.also { service = null }
                }

              Timber.d("Stopping UPnP stack")
              runCatching { toStop?.shutdown() }.onFailure { Timber.w(it, "UPnP shutdown failed") }
            },
            "upnp-shutdown",
          ).also {
            it.isDaemon = true
            it.start()
          }
      }
    }

    private fun start(): UpnpService {
      Timber.d("Starting UPnP stack")

      val stack =
        object : UpnpServiceImpl(AndroidUpnpServiceConfiguration()) {
          override fun createRouter(
            protocolFactory: ProtocolFactory,
            registry: Registry,
          ): Router = AndroidRouter(configuration, protocolFactory, context)

          override fun shutdown() {
            (router as? AndroidRouter)?.unregisterBroadcastReceiver()
            super.shutdown(true)
          }
        }

      stack.startup()
      return stack
    }

    companion object {
      private const val SHUTDOWN_GRACE_MS = 5_000L
    }
  }
