package org.grakovne.lissen.channel.common

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

class ApiClient(
  host: String,
  requestHeaders: List<ServerRequestHeader>?,
  preferences: LissenSharedPreferences,
  context: Context,
) {
  val cacheSize = 10L * 1024 * 1024
  val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)
  private val httpClient = createOkHttpClient(requestHeaders, preferences = preferences, cache = cache)

  val retrofit: Retrofit =
    Retrofit
      .Builder()
      .baseUrl(host)
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
