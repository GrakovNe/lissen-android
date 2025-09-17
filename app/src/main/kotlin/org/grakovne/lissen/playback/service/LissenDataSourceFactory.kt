package org.grakovne.lissen.playback.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

@OptIn(UnstableApi::class)
class LissenDataSourceFactory(
  private val baseContext: Context,
  private val mediaCache: Cache,
  private val requestHeadersProvider: RequestHeadersProvider,
  private val sharedPreferences: LissenSharedPreferences,
  private val mediaProvider: LissenMediaProvider,
) : DataSource.Factory {
  private val upstreamFactory by lazy {
    val requestHeaders =
      requestHeadersProvider
        .fetchRequestHeaders()
        .associate { it.name to it.value }
		
    OkHttpDataSource
      .Factory(
        createOkHttpClient(
          requestHeaders = requestHeadersProvider.fetchRequestHeaders(),
          preferences = sharedPreferences,
        ),
      ).setDefaultRequestProperties(requestHeaders)
  }
	
  private val defaultFactory by lazy {
    CacheDataSource
      .Factory()
      .setCache(mediaCache)
      .setUpstreamDataSourceFactory(DefaultDataSource.Factory(baseContext, upstreamFactory))
      .setCacheWriteDataSinkFactory(
        CacheDataSink
          .Factory()
          .setCache(mediaCache)
          .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE),
      ).setFlags(
        CacheDataSource.FLAG_BLOCK_ON_CACHE or
          CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
      )
  }

  override fun createDataSource(): DataSource {
    val actualDataSource = defaultFactory.createDataSource()
		
    return object : DataSource by actualDataSource {
      override fun open(dataSpec: DataSpec): Long {
        val resolvedUri =
          if (dataSpec.uri.scheme == "lissen") {
            val parts =
              dataSpec.uri
                .toString()
                .removePrefix("lissen://")
                .split("/")
            val bookId = parts[0]
            val fileId = parts[1]
					
            mediaProvider
              .provideFileUri(bookId, fileId)
              .fold(
                onSuccess = { it },
                onFailure = { dataSpec.uri },
              )
          } else {
            dataSpec.uri
          }
				
        val newDataSpec = dataSpec.buildUpon().setUri(resolvedUri).build()
        return actualDataSource.open(newDataSpec)
      }
    }
  }
}
