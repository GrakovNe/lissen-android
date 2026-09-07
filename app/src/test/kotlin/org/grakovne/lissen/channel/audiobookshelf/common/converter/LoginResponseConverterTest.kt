package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.user.LoggedUserResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoginResponseConverterTest {
  private val converter = LoginResponseConverter()

  @Test
  fun `maps user tokens, username and preferred library`() {
    val response =
      LoggedUserResponse(
        user = User(id = "u1", token = "token", refreshToken = "refresh", accessToken = "access", username = "alice"),
        userDefaultLibraryId = "lib-1",
      )

    val result = converter.apply(response)

    assertEquals("token", result.token)
    assertEquals("access", result.accessToken)
    assertEquals("refresh", result.refreshToken)
    assertEquals("alice", result.username)
    assertEquals("lib-1", result.preferredLibraryId)
  }

  @Test
  fun `null userDefaultLibraryId produces null preferred library`() {
    val response =
      LoggedUserResponse(
        user = User(id = "u1", token = null, refreshToken = null, accessToken = null, username = "alice"),
        userDefaultLibraryId = null,
      )

    val result = converter.apply(response)

    assertNull(result.preferredLibraryId)
    assertNull(result.token)
  }
}
