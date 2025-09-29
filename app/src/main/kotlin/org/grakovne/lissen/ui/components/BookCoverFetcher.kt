package org.grakovne.lissen.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
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
  private val uri: String,
  private val options: Options,
) : Fetcher {
  override suspend fun fetch(): FetchResult? =
    when (
      val response =
        mediaChannel
          .fetchBookCover(
            bookId = uri,
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

        SourceFetchResult(
          source = imageSource,
          mimeType = null,
          dataSource = coil3.decode.DataSource.DISK,
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
  ): BookCoverFetcher = BookCoverFetcher(dataProvider, data.toString(), options)
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
      .components { add(bookCoverFetcherFactory) }
      .build()
}

fun Dimension.pxOrNull(): Int? = (this as? Dimension.Pixels)?.px
