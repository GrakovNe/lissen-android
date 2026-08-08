package org.grakovne.lissen.content

import java.security.MessageDigest

fun calculateAccountHash(
  host: String?,
  username: String?,
): String =
  MessageDigest
    .getInstance("SHA-256")
    .digest("${host.orEmpty()}|${username.orEmpty()}".toByteArray())
    .joinToString("") { "%02x".format(it) }
