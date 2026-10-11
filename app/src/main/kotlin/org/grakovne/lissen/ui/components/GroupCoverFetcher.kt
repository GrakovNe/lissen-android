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
import org.grakovne.lissen.content.cache.temporary.GroupCoverProvider

/** The covers of up to a few items of a group (a series, a playlist) stacked into one tile. */
data class GroupCoverKey(
  val groupId: String,
  val coverItemIds: List<String>,
)

class GroupCoverFetcher(
  private val groupCoverProvider: GroupCoverProvider,
  private val key: GroupCoverKey,
) : Fetcher {
  override suspend fun fetch(): FetchResult? =
    when (val response = groupCoverProvider.provideGroupCover(key.groupId, key.coverItemIds)) {
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

class GroupCoverFetcherFactory(
  private val groupCoverProvider: GroupCoverProvider,
) : Fetcher.Factory<GroupCoverKey> {
  override fun create(
    data: GroupCoverKey,
    options: Options,
    imageLoader: ImageLoader,
  ): GroupCoverFetcher = GroupCoverFetcher(groupCoverProvider, data)
}

class GroupCoverKeyer : Keyer<GroupCoverKey> {
  override fun key(
    data: GroupCoverKey,
    options: Options,
  ): String = "group:${data.groupId}:${data.coverItemIds.joinToString(",")}"
}
