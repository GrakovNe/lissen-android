package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.util.Base64

fun encodeLibraryFilter(
  key: String,
  value: String,
): String = "$key.${Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
