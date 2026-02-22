package org.grakovne.lissen.content

import android.content.res.AssetFileDescriptor
import android.net.Uri
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
import org.grakovne.lissen.channel.common.OperationResult

private data class CoverMetadata(
  val bookId: String,
  val width: Int?,
)

private val regex = """/(?:crop_(\d+)|raw)/([^/]+)$""".toRegex()

private fun String.parseCoverUri(): CoverMetadata? =
  regex.find(this)?.let { match ->
    val (widthStr, bookId) = match.destructured
    CoverMetadata(
      bookId = bookId,
      width = widthStr.toIntOrNull(),
    )
  }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LissenMediaProviderEntryPoint {
  fun getLissenMediaProvider(): LissenMediaProvider
}

class ExternalCoverProvider : FileProvider() {
  companion object {
    fun coverUri(
      bookId: String,
      width: Int? = null,
    ) = (
      width?.let {
        "content://${BuildConfig.APPLICATION_ID}.cover/cover/crop_$width/$bookId"
      } ?: "content://${BuildConfig.APPLICATION_ID}.cover/cover/raw/$bookId"
    ).toUri()
  }

  private val lissenMediaProvider by lazy {
    EntryPointAccessors
      .fromApplication(
        context!!.applicationContext,
        LissenMediaProviderEntryPoint::class.java,
      ).getLissenMediaProvider()
  }

  override fun openAssetFile(
    uri: Uri,
    mode: String,
  ): AssetFileDescriptor? =
    uri.path?.parseCoverUri()?.let {
      runBlocking(Dispatchers.IO) {
        lissenMediaProvider
          .fetchBookCover(
            bookId = it.bookId,
            width = it.width,
          ).fold(
            onSuccess = { super.openAssetFile(uri, mode) },
            onFailure = { context?.resources?.openRawResourceFd(R.raw.cover_fallback_png) },
          )
      }
    } ?: super.openAssetFile(uri, mode)
}
