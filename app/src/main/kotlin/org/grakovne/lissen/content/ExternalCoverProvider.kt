package org.grakovne.lissen.content

import android.content.res.AssetFileDescriptor
import android.net.Uri
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
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LissenMediaProviderEntryPoint {
  fun getLissenMediaProvider(): LissenMediaProvider
}

class ExternalCoverProvider : FileProvider() {
  companion object {
    fun coverUri(bookId: String) = "content://${BuildConfig.APPLICATION_ID}.cover/cover/$bookId".toUri()
  }

  private val lissenMediaProvider: LissenMediaProvider
    get() {
      val appContext = requireNotNull(context).applicationContext
      return EntryPointAccessors
        .fromApplication(
          appContext,
          LissenMediaProviderEntryPoint::class.java,
        ).getLissenMediaProvider()
    }

  override fun openAssetFile(
    uri: Uri,
    mode: String,
  ): AssetFileDescriptor? {
    val bookId =
      uri.lastPathSegment
        ?: return super.openAssetFile(uri, mode)

    return runBlocking(Dispatchers.IO) {
      lissenMediaProvider
        .fetchBookCover(bookId = bookId)
        .fold(
          onSuccess = { it.toAssetFileDescriptor() },
          onFailure = { context?.resources?.openRawResourceFd(R.raw.cover_fallback_png) },
        )
    }
  }

  private fun File.toAssetFileDescriptor() =
    AssetFileDescriptor(
      ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY),
      0,
      AssetFileDescriptor.UNKNOWN_LENGTH,
    )
}
