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
import okio.BufferedSource
import okio.buffer
import okio.source
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.content.LissenMediaProvider
import java.io.ByteArrayOutputStream
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
        val inputStream = response.data
        val original = BitmapFactory.decodeStream(inputStream)
        withContext(Dispatchers.IO) {
          inputStream.close()
        }

        val resultSource =
          when (original.width == original.height) {
            true -> source(original)
            false -> runCatching { sourceWithBackdropBlur(original) }.getOrElse { source(original) }
          }

        return SourceResult(
          source = ImageSource(resultSource, context),
          mimeType = "image/png",
          dataSource = coil.decode.DataSource.NETWORK,
        )
      }
    }
  }

  private fun source(original: Bitmap): BufferedSource {
    val outputStream = ByteArrayOutputStream()
    original.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()

    return byteArray.inputStream().source().buffer()
  }

  private fun sourceWithBackdropBlur(original: Bitmap): BufferedSource {
    val width = original.width
    val height = original.height

    val size = maxOf(width, height)

    val blurred =
      HokoBlur
        .with(context)
        .scheme(SCHEME_NATIVE)
        .mode(MODE_STACK)
        .radius(32)
        .forceCopy(true)
        .blur(original.scale(size, size))

    val result = createBitmap(size, size)
    val canvas = Canvas(result)
    canvas.drawBitmap(blurred, 0f, 0f, null)

    val left = ((size - width) / 2f)
    val top = ((size - height) / 2f)
    canvas.drawBitmap(original, left, top, null)

    val outputStream = ByteArrayOutputStream()
    result.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val resultBytes = outputStream.toByteArray()

    return resultBytes.inputStream().source().buffer()
  }
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
