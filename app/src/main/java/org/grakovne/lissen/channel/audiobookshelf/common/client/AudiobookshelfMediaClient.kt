package org.grakovne.lissen.channel.audiobookshelf.common.client

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface AudiobookshelfMediaClient {
  @GET("/api/items/{itemId}/cover")
  @Streaming
  suspend fun getItemCover(
    @Path("itemId") itemId: String,
    @Query("raw") raw: Int = 1,
  ): Response<ResponseBody>
}
