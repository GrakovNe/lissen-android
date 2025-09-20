package org.grakovne.lissen.ui.components

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.content.LissenMediaProvider
import java.io.File
import javax.inject.Singleton

class BookCoverFetcher(
  private val mediaChannel: LissenMediaProvider,
  private val uri: Uri,
  private val options: Options,
) : Fetcher {
  override suspend fun fetch(): FetchResult? =
    when (
      val response =
        mediaChannel
          .fetchBookCover(
            bookId = uri.toString(),
            width = options.size.width.pxOrNull(),
          )
    ) {
      is ApiResult.Error -> null
      is ApiResult.Success -> {
        val stream: File = response.data
        val imageSource =
          ImageSource(
            file = stream.toOkioPath(),
            fileSystem = FileSystem.SYSTEM,
          )

        SourceResult(
          source = imageSource,
          mimeType = null,
          dataSource = coil.decode.DataSource.DISK,
        )
      }
    }
}

class BookCoverFetcherFactory(
  private val dataProvider: LissenMediaProvider,
) : Fetcher.Factory<Uri> {
  override fun create(
    data: Uri,
    options: Options,
    imageLoader: ImageLoader,
  ) = BookCoverFetcher(dataProvider, data, options)
}

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
  @Singleton
  @Provides
  fun provideBookCoverFetcherFactory(mediaChannel: LissenMediaProvider): BookCoverFetcherFactory = BookCoverFetcherFactory(mediaChannel)

  @Singleton
  @Provides
  fun provideCustomImageLoader(
    @ApplicationContext context: Context,
    bookCoverFetcherFactory: BookCoverFetcherFactory,
  ): ImageLoader =
    ImageLoader
      .Builder(context)
      .components {
        add(CoverCacheKeyHolder())
        add(bookCoverFetcherFactory)
      }.build()
}

fun Dimension.pxOrNull(): Int? = (this as? Dimension.Pixels)?.px

class CoverCacheKeyHolder : Keyer<Uri> {
  override fun key(
    data: Uri,
    options: Options,
  ): String {
    val width = options.size.width.pxOrNull()

    return "cover:$data:w=${width ?: "auto"}"
  }
}
