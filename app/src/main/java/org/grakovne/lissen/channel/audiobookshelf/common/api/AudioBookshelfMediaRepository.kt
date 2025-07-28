package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.util.Log
import okhttp3.ResponseBody
import okio.Buffer
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.BinaryApiClient
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfMediaRepository
@Inject
constructor(
  private val audioBookShelfApiCallService: AudioBookShelfApiCallService,
) {
  
  suspend fun fetchBookCover(itemId: String): ApiResult<Buffer> =
    audioBookShelfApiCallService.makeMediaRequest {
      it.getItemCover(
        itemId = itemId
      )
    }
}
