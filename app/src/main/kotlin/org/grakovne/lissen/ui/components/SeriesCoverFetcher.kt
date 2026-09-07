package org.grakovne.lissen.ui.components

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.temporary.SeriesCoverProvider

data class SeriesCoverKey(
  val seriesId: String,
  val coverItemIds: List<String>,
)

class SeriesCoverFetcher(
  private val seriesCoverProvider: SeriesCoverProvider,
  private val key: SeriesCoverKey,
) : Fetcher {
  override suspend fun fetch(): FetchResult? =
    when (val response = seriesCoverProvider.provideSeriesCover(key.seriesId, key.coverItemIds)) {
      is OperationResult.Error -> {
        null
      }

      is OperationResult.Success -> {
        SourceFetchResult(
          source =
            ImageSource(
              file = response.data.toOkioPath(),
              fileSystem = FileSystem.SYSTEM,
            ),
          mimeType = null,
          dataSource = DataSource.DISK,
        )
      }
    }
}

class SeriesCoverFetcherFactory(
  private val seriesCoverProvider: SeriesCoverProvider,
) : Fetcher.Factory<SeriesCoverKey> {
  override fun create(
    data: SeriesCoverKey,
    options: Options,
    imageLoader: ImageLoader,
  ): SeriesCoverFetcher = SeriesCoverFetcher(seriesCoverProvider, data)
}

class SeriesCoverKeyer : Keyer<SeriesCoverKey> {
  override fun key(
    data: SeriesCoverKey,
    options: Options,
  ): String = "series:${data.seriesId}:${data.coverItemIds.joinToString(",")}"
}
