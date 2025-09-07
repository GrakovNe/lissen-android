package org.grakovne.lissen.channel.common

import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient(
  host: String,
  requestHeaders: List<ServerRequestHeader>?,
  preferences: LissenSharedPreferences,
) {
  private val httpClient = createOkHttpClient(requestHeaders, preferences = preferences)

  val retrofit: Retrofit =
    Retrofit
      .Builder()
      .baseUrl(host)
      .client(httpClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
}
