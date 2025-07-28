package org.grakovne.lissen.channel.audiobookshelf.common.api

import okio.Buffer
import org.grakovne.lissen.channel.common.ApiResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfMediaRepository
@Inject
constructor(
  private val audioBookShelfApiCallService: AudioBookShelfApiCallService,
) {
  
  suspend fun fetchBookCover(itemId: String): ApiResult<Buffer> = audioBookShelfApiCallService
    .makeRequest { it.getItemCover(itemId = itemId) }
    .map {
      Buffer().apply {
        writeAll(it.source())
      }
    }
}
