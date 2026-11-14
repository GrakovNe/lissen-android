package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

class ServerUnreachableTest {
  @Test
  fun `a failed connection is unreachable`() {
    assertTrue(isServerUnreachable(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null))
  }

  @Test
  fun `a connection timeout is unreachable`() {
    assertTrue(isServerUnreachable(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null))
  }

  @Test
  fun `an unknown host deep in the causes is unreachable`() {
    val cause = IOException("wrapped", IOException("again", UnknownHostException("my-server.ts.net")))

    assertTrue(isServerUnreachable(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause))
  }

  @Test
  fun `a reset connection mid-stream is unreachable`() {
    assertTrue(isServerUnreachable(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, SocketException("Connection reset")))
  }

  @Test
  fun `an error status from the server is not unreachable`() {
    assertFalse(isServerUnreachable(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null))
  }

  @Test
  fun `a decoding failure is not unreachable`() {
    assertFalse(isServerUnreachable(PlaybackException.ERROR_CODE_DECODING_FAILED, IOException("bad frame")))
  }
}
