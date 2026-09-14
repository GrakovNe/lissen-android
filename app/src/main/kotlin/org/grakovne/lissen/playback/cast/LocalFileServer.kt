package org.grakovne.lissen.playback.cast

import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serves downloaded audio files to renderers on the LAN through NanoHTTPD. Only files
 * that were explicitly exposed are reachable, under a random per-process secret, so the
 * download folder is never browsable from the network.
 */
@Singleton
class LocalFileServer
  @Inject
  constructor() {
    private val secret = randomSecret()
    private val exposed = ConcurrentHashMap<String, Exposed>()
    private var server: Server? = null

    private data class Exposed(
      val file: File,
      val mimeType: String,
    )

    @Synchronized
    fun start(): Int {
      server?.takeIf { it.isAlive }?.let { return it.listeningPort }

      val instance = Server()
      instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
      server = instance
      Timber.d("Local file server started on port ${instance.listeningPort}")
      return instance.listeningPort
    }

    @Synchronized
    fun stop() {
      server?.stop()
      server = null
      exposed.clear()
    }

    /** Makes [file] reachable and returns the URL a device at [remoteHost] can fetch it from. */
    fun expose(
      key: String,
      file: File,
      mimeType: String,
      remoteHost: String,
    ): String? {
      val port = start()
      val address = localAddressFor(remoteHost) ?: return null
      exposed[key] = Exposed(file, mimeType)
      return "http://$address:$port/$secret/${URLEncoder.encode(key, "UTF-8")}"
    }

    private inner class Server : NanoHTTPD(0) {
      override fun serve(session: IHTTPSession): Response {
        val segments = session.uri.trimStart('/').split('/')
        if (segments.size != 2 || segments[0] != secret) {
          return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        }

        val key = runCatching { URLDecoder.decode(segments[1], "UTF-8") }.getOrDefault(segments[1])
        val entry = exposed[key] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        val file = entry.file
        if (!file.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")

        val length = file.length()
        val rangeHeader = session.headers["range"]
        val range = rangeHeader?.let { parseRange(it, length) }

        if (rangeHeader != null && range == null) {
          return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
            addHeader("Content-Range", "bytes */$length")
          }
        }

        val start = range?.first ?: 0L
        val end = range?.second ?: (length - 1)
        val count = end - start + 1

        val input = FileInputStream(file).apply { skipFully(start) }
        val status = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK

        return newFixedLengthResponse(status, entry.mimeType, input, count).apply {
          addHeader("Accept-Ranges", "bytes")
          if (range != null) addHeader("Content-Range", "bytes $start-$end/$length")
        }
      }
    }

    companion object {
      private fun randomSecret(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
      }

      private fun FileInputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
          val skipped = skip(remaining)
          if (skipped <= 0) break
          remaining -= skipped
        }
      }

      /** Parses `bytes=a-b`, `bytes=a-` and `bytes=-n`. Returns null when unsatisfiable. */
      internal fun parseRange(
        header: String,
        length: Long,
      ): Pair<Long, Long>? {
        val spec =
          header
            .trim()
            .removePrefix("bytes=")
            .split(',')
            .firstOrNull()
            ?.trim() ?: return null
        val parts = spec.split('-', limit = 2)
        if (parts.size != 2) return null

        val startText = parts[0].trim()
        val endText = parts[1].trim()

        return when {
          startText.isEmpty() && endText.isEmpty() -> {
            null
          }

          startText.isEmpty() -> {
            val suffix = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
            (length - suffix).coerceAtLeast(0) to length - 1
          }

          else -> {
            val start = startText.toLongOrNull() ?: return null
            if (start < 0 || start >= length) return null
            val end = endText.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
            if (end < start) return null
            start to end
          }
        }
      }

      /** The phone's IPv4 address on the interface whose subnet contains [remoteHost]. */
      internal fun localAddressFor(remoteHost: String): String? {
        val remote = runCatching { InetAddress.getByName(remoteHost) }.getOrNull() as? Inet4Address ?: return null

        val candidates =
          runCatching {
            NetworkInterface
              .getNetworkInterfaces()
              ?.toList()
              .orEmpty()
              .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
              .flatMap { networkInterface ->
                networkInterface.interfaceAddresses.mapNotNull { interfaceAddress ->
                  (interfaceAddress.address as? Inet4Address)
                    ?.takeIf { !it.isLoopbackAddress && !it.isAnyLocalAddress && !it.isLinkLocalAddress }
                    ?.let { Triple(networkInterface.name, it, interfaceAddress.networkPrefixLength.toInt()) }
                }
              }
          }.getOrDefault(emptyList())

        fun toInt(address: Inet4Address): Int = address.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xff) }

        candidates
          .firstOrNull { (_, address, prefix) ->
            prefix in 1..32 && (toInt(address) and (-1 shl (32 - prefix))) == (toInt(remote) and (-1 shl (32 - prefix)))
          }?.let { return it.second.hostAddress }

        val preferred = candidates.firstOrNull { it.first.startsWith("wlan") || it.first.startsWith("eth") }
        return (preferred ?: candidates.firstOrNull())?.second?.hostAddress
      }
    }
  }
