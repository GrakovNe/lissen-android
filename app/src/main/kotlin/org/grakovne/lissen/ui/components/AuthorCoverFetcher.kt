package org.grakovne.lissen.ui.components

import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import java.io.File

data class AuthorCoverKey(
  val authorId: String,
)

class AuthorCoverFetcher(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: LissenMediaProvider,
  private val authorId: String,
  options: Options,
) : ImageFetcher(options) {
  override suspend fun resolve(): OperationResult<File> =
    when {
      localOnly -> localCacheRepository.fetchAuthorCover(authorId)
      else -> mediaChannel.fetchAuthorCover(authorId)
    }
}

class AuthorCoverFetcherFactory(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: LissenMediaProvider,
) : Fetcher.Factory<AuthorCoverKey> {
  override fun create(
    data: AuthorCoverKey,
    options: Options,
    imageLoader: ImageLoader,
  ): AuthorCoverFetcher = AuthorCoverFetcher(localCacheRepository, mediaChannel, data.authorId, options)
}

class AuthorCoverKeyer : Keyer<AuthorCoverKey> {
  override fun key(
    data: AuthorCoverKey,
    options: Options,
  ): String = "author:${data.authorId}"
}
