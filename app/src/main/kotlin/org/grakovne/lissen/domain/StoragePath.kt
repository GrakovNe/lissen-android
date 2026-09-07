package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class StoragePath(
  val path: String,
  val name: String,
)
