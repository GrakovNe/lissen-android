package org.grakovne.lissen.channel.audiobookshelf.common.api

import java.util.Base64

fun encodeLibraryFilter(
  key: String,
  value: String,
): String = "$key.${Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))}"
