package org.grakovne.lissen.ui.components

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.buffer
import okio.source
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.content.LissenMediaProvider
import javax.inject.Singleton

class BookCoverFetcher(
    private val mediaChannel: LissenMediaProvider,
    private val uri: Uri,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult? =
        when (val response = mediaChannel.fetchBookCover(uri.toString())) {
            is ApiResult.Error -> null
            is ApiResult.Success -> {
                val stream = response.data
                val source = stream.source().buffer()
                val imageSource = ImageSource(source, context)

                SourceResult(
                    source = imageSource,
                    mimeType = null,
                    dataSource = coil.decode.DataSource.NETWORK,
                )
            }
        }
}

class BookCoverFetcherFactory(
    private val dataProvider: LissenMediaProvider,
    private val context: Context,
) : Fetcher.Factory<Uri> {

    override fun create(data: Uri, options: Options, imageLoader: ImageLoader) =
        BookCoverFetcher(dataProvider, data, context)
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
    ): ImageLoader {
        return ImageLoader
            .Builder(context)
            .components { add(bookCoverFetcherFactory) }
            .build()
    }
}
