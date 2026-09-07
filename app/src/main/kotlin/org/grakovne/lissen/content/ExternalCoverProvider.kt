package org.grakovne.lissen.content

import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.R
import org.grakovne.lissen.content.cache.temporary.SeriesCoverProvider
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LissenMediaProviderEntryPoint {
  fun getLissenMediaProvider(): LissenMediaProvider
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SeriesCoverProviderEntryPoint {
  fun getSeriesCoverProvider(): SeriesCoverProvider
}

class ExternalCoverProvider : FileProvider() {
  companion object {
    const val BOOK_PATH = "book"
    const val SERIES_PATH = "series"

    fun bookCoverUri(bookId: String) = "content://${BuildConfig.APPLICATION_ID}.cover/$BOOK_PATH/$bookId".toUri()

    /**
     * URI for a composite series cover built from [coverItemIds] (up to 3 book IDs).
     * Format: content://<authority>/series/<seriesId>/<bookId1>,<bookId2>,...
     */
    fun seriesCoverUri(
      seriesId: String,
      coverItemIds: List<String>,
    ): Uri {
      val encodedIds = coverItemIds.take(3).joinToString(",")
      return "content://${BuildConfig.APPLICATION_ID}.cover/$SERIES_PATH/$seriesId/$encodedIds".toUri()
    }
  }

  private val lissenMediaProvider: LissenMediaProvider
    get() {
      val appContext = requireNotNull(context).applicationContext
      return EntryPointAccessors
        .fromApplication(appContext, LissenMediaProviderEntryPoint::class.java)
        .getLissenMediaProvider()
    }

  private val seriesCoverProvider: SeriesCoverProvider
    get() {
      val appContext = requireNotNull(context).applicationContext
      return EntryPointAccessors
        .fromApplication(appContext, SeriesCoverProviderEntryPoint::class.java)
        .getSeriesCoverProvider()
    }

  override fun openAssetFile(
    uri: Uri,
    mode: String,
  ): AssetFileDescriptor? {
    val segments = uri.pathSegments
    if (segments.isEmpty()) return super.openAssetFile(uri, mode)

    return when (segments[0]) {
      BOOK_PATH -> openBookCoverFile(uri)
      SERIES_PATH -> openSeriesCoverFile(uri)
      else -> fallbackCover()
    }
  }

  override fun openTypedAssetFile(
    uri: Uri,
    mimeTypeFilter: String,
    opts: Bundle?,
  ) = openAssetFile(uri, "r")

  private fun openBookCoverFile(uri: Uri): AssetFileDescriptor? {
    val bookId = uri.lastPathSegment ?: return fallbackCover()

    return runBlocking(Dispatchers.IO) {
      lissenMediaProvider
        .fetchBookCover(bookId = bookId)
        .fold(
          onSuccess = { it.toAssetFileDescriptor() },
          onFailure = { fallbackCover() },
        )
    }
  }

  private fun openSeriesCoverFile(uri: Uri): AssetFileDescriptor? {
    // URI pattern: /series/<seriesId>/<bookId1>,<bookId2>,...
    val segments = uri.pathSegments
    if (segments.size < 3) return fallbackCover()

    val seriesId = segments[1]
    val coverItemIds = segments[2].split(",").filter { it.isNotBlank() }

    return runBlocking(Dispatchers.IO) {
      seriesCoverProvider
        .provideSeriesCover(seriesId = seriesId, coverItemIds = coverItemIds)
        .fold(
          onSuccess = { it.toAssetFileDescriptor() },
          onFailure = { fallbackCover() },
        )
    }
  }

  private fun fallbackCover(): AssetFileDescriptor? = context?.resources?.openRawResourceFd(R.raw.cover_fallback_png)

  private fun File.toAssetFileDescriptor() =
    AssetFileDescriptor(
      ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY),
      0,
      AssetFileDescriptor.UNKNOWN_LENGTH,
    )
}
