package org.grakovne.lissen.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.hoko.blur.HokoBlur
import com.hoko.blur.HokoBlur.MODE_STACK
import com.hoko.blur.HokoBlur.SCHEME_NATIVE
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.content.LissenMediaProvider
import java.io.OutputStream
import javax.inject.Singleton

class BookCoverFetcher(
  private val mediaChannel: LissenMediaProvider,
  private val uri: Uri,
  private val context: Context,
) : Fetcher {
  override suspend fun fetch(): FetchResult? {
    return when (val response = mediaChannel.fetchBookCover(uri.toString())) {
      is ApiResult.Error -> null
      is ApiResult.Success -> {
        val image: ByteArray = response.data
        val byteSource = Buffer().write(image)
        val dimensions = getImageDimensions(image)
        val resultSource =
          when (dimensions?.first == dimensions?.second) {
            true -> byteSource.buffer
            false -> runCatching { sourceWithBackdropBlur(image) }.getOrElse { byteSource.buffer }
          }

        return SourceResult(
          source = ImageSource(resultSource, context),
          mimeType = null,
          dataSource = coil.decode.DataSource.MEMORY,
        )
      }
    }
  }

  private fun getImageDimensions(image: ByteArray): Pair<Int, Int>? =
    try {
      val boundsOptions =
        BitmapFactory.Options().apply {
          inJustDecodeBounds = true
        }

      BitmapFactory.decodeByteArray(image, 0, image.size, boundsOptions)
      boundsOptions.outWidth to boundsOptions.outHeight
    } catch (ex: Exception) {
      null
    }

  private suspend fun sourceWithBackdropBlur(image: ByteArray): BufferedSource =
    withContext(Dispatchers.IO) {
      val original = BitmapFactory.decodeByteArray(image, 0, image.size)
      val width = original.width
      val height = original.height

      val size = maxOf(width, height)

      val blurred =
        HokoBlur
          .with(context)
          .scheme(SCHEME_NATIVE)
          .mode(MODE_STACK)
          .radius(24)
          .forceCopy(true)
          .blur(original.scale(size, size))

      val result = createBitmap(size, size)
      val canvas = Canvas(result)
      canvas.drawBitmap(blurred, 0f, 0f, null)

      val left = ((size - width) / 2f)
      val top = ((size - height) / 2f)
      canvas.drawBitmap(original, left, top, null)

      val buffer = Buffer()
      result.compress(buffer.outputStream())

      buffer
    }

  private fun Bitmap.compress(outputStream: OutputStream) = this.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
}

class BookCoverFetcherFactory(
  private val dataProvider: LissenMediaProvider,
  private val context: Context,
) : Fetcher.Factory<Uri> {
  override fun create(
    data: Uri,
    options: Options,
    imageLoader: ImageLoader,
  ) = BookCoverFetcher(dataProvider, data, context)
}

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
  @Singleton
  @Provides
  fun provideBookCoverFetcherFactory(
    mediaChannel: LissenMediaProvider,
    @ApplicationContext context: Context,
  ): BookCoverFetcherFactory = BookCoverFetcherFactory(mediaChannel, context)

  @Singleton
  @Provides
  fun provideCustomImageLoader(
    @ApplicationContext context: Context,
    bookCoverFetcherFactory: BookCoverFetcherFactory,
  ): ImageLoader =
    ImageLoader
      .Builder(context)
      .components { add(bookCoverFetcherFactory) }
      .build()
}
