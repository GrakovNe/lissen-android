package org.grakovne.lissen.content.cache.common

import java.security.MessageDigest

fun String.toFileKey(): String =
  MessageDigest
    .getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
