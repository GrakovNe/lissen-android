package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoServerResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoUserResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConnectionInfoResponseConverterTest {
  private val converter = ConnectionInfoResponseConverter()

  @Test
  fun `maps username, server version and build number`() {
    val response =
      ConnectionInfoResponse(
        user = ConnectionInfoUserResponse(username = "alice"),
        serverSettings = ConnectionInfoServerResponse(version = "2.1.0", buildNumber = "42"),
      )

    val result = converter.apply(response)

    assertEquals("alice", result.username)
    assertEquals("2.1.0", result.serverVersion)
    assertEquals("42", result.buildNumber)
  }

  @Test
  fun `null serverSettings produces null version and build number`() {
    val response = ConnectionInfoResponse(user = ConnectionInfoUserResponse(username = "alice"), serverSettings = null)

    val result = converter.apply(response)

    assertNull(result.serverVersion)
    assertNull(result.buildNumber)
  }
}
