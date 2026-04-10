package org.grakovne.lissen.channel.common

import android.content.Context
import com.squareup.moshi.Moshi
import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader
import org.grakovne.lissen.lib.domain.fixUriScheme
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiClient(
  host: String,
  requestHeaders: List<ServerRequestHeader>?,
  preferences: LissenSharedPreferences,
  context: Context? = null,
) {
  private val httpClient = createOkHttpClient(requestHeaders, preferences = preferences, context = context)

  val retrofit: Retrofit? =
    runCatching {
      Retrofit
        .Builder()
        .baseUrl(host.fixUriScheme())
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
    }.getOrNull()

  companion object {
    private val moshi: Moshi =
      Moshi
        .Builder()
        .build()
  }
}
