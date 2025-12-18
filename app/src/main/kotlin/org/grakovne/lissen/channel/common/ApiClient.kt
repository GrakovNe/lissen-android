package org.grakovne.lissen.channel.common

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader
import org.grakovne.lissen.lib.domain.fixUriScheme
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiClient(
  host: String,
  requestHeaders: List<ServerRequestHeader>?,
  preferences: LissenSharedPreferences,
) {
  private val httpClient = createOkHttpClient(requestHeaders, preferences = preferences)

  val retrofit: Retrofit =
    Retrofit
      .Builder()
      .baseUrl(host.fixUriScheme())
      .client(httpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()

  companion object {
    private val moshi: Moshi =
      Moshi
        .Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
  }
}
