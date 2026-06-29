package org.grakovne.lissen.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.SeriesCoverProvider
import java.io.File
import javax.inject.Singleton

data class BookCoverKey(
  val bookId: String,
)

class BookCoverFetcher(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: LissenMediaProvider,
  private val bookId: String,
  options: Options,
) : ImageFetcher(options) {
  override suspend fun resolve(): OperationResult<File> =
    when {
      localOnly -> localCacheRepository.fetchBookCover(bookId)
      else -> mediaChannel.fetchBookCover(bookId)
    }
}

class BookCoverFetcherFactory(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: LissenMediaProvider,
) : Fetcher.Factory<BookCoverKey> {
  override fun create(
    data: BookCoverKey,
    options: Options,
    imageLoader: ImageLoader,
  ): BookCoverFetcher = BookCoverFetcher(localCacheRepository, mediaChannel, data.bookId, options)
}

class BookCoverKeyer : Keyer<BookCoverKey> {
  override fun key(
    data: BookCoverKey,
    options: Options,
  ): String = "book:${data.bookId}"
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
  fun provideAuthorCoverFetcherFactory(
    localCacheRepository: LocalCacheRepository,
    mediaChannel: LissenMediaProvider,
  ): AuthorCoverFetcherFactory = AuthorCoverFetcherFactory(localCacheRepository, mediaChannel)

  @Singleton
  @Provides
  fun provideSeriesCoverFetcherFactory(seriesCoverProvider: SeriesCoverProvider): SeriesCoverFetcherFactory =
    SeriesCoverFetcherFactory(seriesCoverProvider)

  @Singleton
  @Provides
  fun provideCustomImageLoader(
    @ApplicationContext context: Context,
    bookCoverFetcherFactory: BookCoverFetcherFactory,
    authorCoverFetcherFactory: AuthorCoverFetcherFactory,
    seriesCoverFetcherFactory: SeriesCoverFetcherFactory,
  ): ImageLoader =
    ImageLoader
      .Builder(context)
      .components {
        add(bookCoverFetcherFactory)
        add(authorCoverFetcherFactory)
        add(seriesCoverFetcherFactory)
        add(BookCoverKeyer(), BookCoverKey::class)
        add(AuthorCoverKeyer(), AuthorCoverKey::class)
        add(SeriesCoverKeyer(), SeriesCoverKey::class)
      }.build()
}
