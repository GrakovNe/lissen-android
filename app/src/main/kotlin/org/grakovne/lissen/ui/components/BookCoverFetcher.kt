package org.grakovne.lissen.ui.components

import android.content.Context
import coil3.Extras
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
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import java.io.File
import javax.inject.Singleton

class BookCoverFetcher(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: LissenMediaProvider,
  private val uri: String,
  private val options: Options,
) : Fetcher {
  override suspend fun fetch(): FetchResult? {
    val localOnly = options.extras[LocalOnlyKey] ?: false

    val response =
      when (localOnly) {
        true -> localCacheRepository.fetchBookCover(uri)
        false -> mediaChannel.fetchBookCover(uri, options.size.width.pxOrNull())
      }

    return when (response) {
      is OperationResult.Error -> null
      is OperationResult.Success -> {
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

  companion object {
    val LocalOnlyKey = Extras.Key(false)
  }
}

class BookCoverFetcherFactory(
  private val localCacheRepository: LocalCacheRepository,
  private val dataProvider: LissenMediaProvider,
) : Fetcher.Factory<Uri> {
  override fun create(
    data: Uri,
    options: Options,
    imageLoader: ImageLoader,
  ): BookCoverFetcher = BookCoverFetcher(localCacheRepository, dataProvider, data.toString(), options)
}

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
  @Singleton
  @Provides
  fun provideBookCoverFetcherFactory(
    localCacheRepository: LocalCacheRepository,
    mediaChannel: LissenMediaProvider,
  ): BookCoverFetcherFactory = BookCoverFetcherFactory(localCacheRepository, mediaChannel)

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

private fun Dimension.pxOrNull(): Int? = (this as? Dimension.Pixels)?.px
