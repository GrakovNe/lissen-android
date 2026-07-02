package org.grakovne.lissen.playback.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.IOException

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
          context = baseContext,
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
        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
      )
  }

  override fun createDataSource(): DataSource =
    LocalFallbackDataSource(
      upstream = defaultFactory.createDataSource(),
      local = FileDataSource(),
      mediaProvider = mediaProvider,
    )
}

@OptIn(UnstableApi::class)
internal class LocalFallbackDataSource(
  private val upstream: DataSource,
  private val local: DataSource,
  private val mediaProvider: LissenMediaProvider,
) : DataSource by upstream {
  private var activeDataSource: DataSource = upstream

  private var resolvedItemId: String? = null
  private var resolvedFileId: String? = null
  private var resolvedSpec: DataSpec? = null
  private var bytesRead: Long = 0
  private var usingLocal: Boolean = false

  override fun open(dataSpec: DataSpec): Long {
    val (itemId, fileId) = unapply(dataSpec.uri) ?: return openPassthrough(dataSpec)

    val resolvedUri =
      mediaProvider
        .provideFileUri(itemId, fileId)
        .fold(
          onSuccess = { it },
          onFailure = { dataSpec.uri },
        )

    Timber.d("Resolved Uri: $resolvedUri for itemId = $itemId and fileId = $fileId")

    val spec =
      dataSpec
        .buildUpon()
        .setUri(resolvedUri)
        .build()

    usingLocal = resolvedUri.scheme == "file"
    activeDataSource = if (usingLocal) local else upstream

    resolvedItemId = itemId
    resolvedFileId = fileId
    resolvedSpec = spec
    bytesRead = 0

    return activeDataSource.open(spec)
  }

  override fun read(
    buffer: ByteArray,
    offset: Int,
    length: Int,
  ): Int =
    try {
      activeDataSource.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
    } catch (networkError: IOException) {
      if (switchToLocalIfAvailable()) {
        activeDataSource.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
      } else {
        throw networkError
      }
    }

  private fun openPassthrough(dataSpec: DataSpec): Long {
    usingLocal = false
    activeDataSource = upstream

    resolvedItemId = null
    resolvedFileId = null
    resolvedSpec = null
    bytesRead = 0

    return upstream.open(dataSpec)
  }

  private fun switchToLocalIfAvailable(): Boolean {
    if (usingLocal) return false

    val itemId = resolvedItemId ?: return false
    val fileId = resolvedFileId ?: return false
    val spec = resolvedSpec ?: return false

    val localUri =
      mediaProvider
        .provideFileUri(itemId, fileId)
        .fold(onSuccess = { it }, onFailure = { null })
        ?.takeIf { it.scheme == "file" }
        ?: return false

    return try {
      runCatching { activeDataSource.close() }

      val resumeSpec =
        spec
          .subrange(bytesRead)
          .buildUpon()
          .setUri(localUri)
          .build()

      local.open(resumeSpec)
      activeDataSource = local
      usingLocal = true

      Timber.d("Seamlessly switched to local file $localUri at offset ${spec.position + bytesRead}")
      true
    } catch (localError: IOException) {
      Timber.w(localError, "Failed to fall back to local file for itemId = $itemId, fileId = $fileId")
      false
    }
  }

  override fun getUri() = activeDataSource.uri

  override fun close() = activeDataSource.close()
}
