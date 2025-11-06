package org.grakovne.lissen.channel.common

import androidx.annotation.Keep

@Keep
data class AuthData(
  val methods: List<AuthMethod>,
  val oauthLoginText: String?,
) {
  companion object {
    val empty = AuthData(emptyList(), null)
  }
}

enum class AuthMethod {
  CREDENTIALS,
  O_AUTH,
}
