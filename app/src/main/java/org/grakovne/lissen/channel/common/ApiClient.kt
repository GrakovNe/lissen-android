package org.grakovne.lissen.channel.common

import org.grakovne.lissen.domain.connection.ServerRequestHeader
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient(
  host: String,
  requestHeaders: List<ServerRequestHeader>?,
  token: String? = null,
  accessToken: String? = null,
) {
  private val httpClient = createOkHttpClient(requestHeaders, token, accessToken)

  val retrofit: Retrofit =
    Retrofit
      .Builder()
      .baseUrl(host)
      .client(httpClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
}
