package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * True when the player stopped because the server could not be reached: no route, no DNS, a
 * refused or reset connection, a timeout. An answer from the server, even an error status, means
 * it was reachable. The error code is enough on its own: a [PlaybackException] that came through
 * the media controller has lost its cause.
 */
internal fun PlaybackException.isServerUnreachable(): Boolean = isServerUnreachable(errorCode, cause)

internal fun isServerUnreachable(
  errorCode: Int,
  cause: Throwable?,
): Boolean {
  if (errorCode in UNREACHABLE_CODES) return true

  return generateSequence(cause) { it.cause }
    .take(MAX_CAUSE_DEPTH)
    .any { it.isConnectivityFailure() }
}

private fun Throwable.isConnectivityFailure(): Boolean =
  this is UnknownHostException ||
    this is ConnectException ||
    this is NoRouteToHostException ||
    this is SocketTimeoutException ||
    this is SocketException

private val UNREACHABLE_CODES =
  setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
  )

private const val MAX_CAUSE_DEPTH = 10
