package org.grakovne.lissen.ui.components

import coil3.Extras
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.grakovne.lissen.channel.common.OperationResult
import java.io.File

abstract class ImageFetcher(
  private val options: Options,
) : Fetcher {
  protected val localOnly: Boolean
    get() = options.extras[LocalOnlyKey] ?: false

  protected abstract suspend fun resolve(): OperationResult<File>

  override suspend fun fetch(): FetchResult? =
    when (val response = resolve()) {
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

  companion object {
    val LocalOnlyKey = Extras.Key(false)
  }
}
