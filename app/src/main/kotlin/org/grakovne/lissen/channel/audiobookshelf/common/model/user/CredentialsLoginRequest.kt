package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep

@Keep
data class CredentialsLoginRequest(
  val username: String,
  val password: String,
)
