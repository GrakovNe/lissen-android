package org.grakovne.lissen.common

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

val moshi: Moshi =
  Moshi
    .Builder()
    .add(
      UUID::class.java,
      object : JsonAdapter<UUID>() {
        @FromJson
        override fun fromJson(reader: JsonReader): UUID? = reader.nextString()?.let { UUID.fromString(it) }

        @ToJson
        override fun toJson(
          writer: JsonWriter,
          value: UUID?,
        ) {
          writer.value(value?.toString())
        }
      },
    ).build()
