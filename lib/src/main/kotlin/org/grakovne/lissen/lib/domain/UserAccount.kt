package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep

@Keep
data class UserAccount(
  val token: String?,
  val accessToken: String?,
  val refreshToken: String?,
  val username: String,
  val preferredLibraryId: String?,
)
