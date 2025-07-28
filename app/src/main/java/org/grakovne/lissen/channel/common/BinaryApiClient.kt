package org.grakovne.lissen.channel.common

import org.grakovne.lissen.domain.connection.ServerRequestHeader
import retrofit2.Retrofit

class BinaryApiClient(
  host: String,
  requestHeaders: List<ServerRequestHeader>?,
  token: String,
  accessToken: String? = null,
) {
  private val httpClient = createOkHttpClient(requestHeaders, token, accessToken)

  val retrofit: Retrofit =
    Retrofit
      .Builder()
      .baseUrl(host)
      .client(httpClient)
      .build()
}
