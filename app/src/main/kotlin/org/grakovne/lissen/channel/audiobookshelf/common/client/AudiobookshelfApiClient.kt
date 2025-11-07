package org.grakovne.lissen.channel.audiobookshelf.common.client

import okhttp3.ResponseBody
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.AuthorItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.ProgressSyncRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.CredentialsLoginRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.LoggedUserResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastSearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface AudiobookshelfApiClient {
  @GET("/api/libraries")
  suspend fun fetchLibraries(): Response<LibraryResponse>

  @GET("/api/libraries/{libraryId}/personalized")
  suspend fun fetchPersonalizedFeed(
    @Path("libraryId") libraryId: String,
  ): Response<List<PersonalizedFeedResponse>>

  @GET("/api/me/progress/{itemId}")
  suspend fun fetchLibraryItemProgress(
    @Path("itemId") itemId: String,
  ): Response<MediaProgressResponse>

  @POST("/api/authorize")
  suspend fun fetchConnectionInfo(): Response<ConnectionInfoResponse>

  @GET("/api/me")
  suspend fun fetchUserInfo(): Response<UserResponse>

  @GET("api/libraries/{libraryId}/items")
  suspend fun fetchLibraryItems(
    @Path("libraryId") libraryId: String,
    @Query("limit") pageSize: Int,
    @Query("page") pageNumber: Int,
    @Query("sort") sort: String,
    @Query("desc") desc: String,
    @Query("minified") minified: String = "1",
  ): Response<LibraryItemsResponse>

  @GET("api/libraries/{libraryId}/items")
  suspend fun fetchPodcastItems(
    @Path("libraryId") libraryId: String,
    @Query("limit") pageSize: Int,
    @Query("page") pageNumber: Int,
    @Query("sort") sort: String,
    @Query("desc") desc: String,
    @Query("minified") minified: String = "1",
  ): Response<PodcastItemsResponse>

  @GET("api/libraries/{libraryId}/search")
  suspend fun searchLibraryItems(
    @Path("libraryId") libraryId: String,
    @Query("q") request: String,
    @Query("limit") limit: Int,
  ): Response<LibrarySearchResponse>

  @GET("api/libraries/{libraryId}/search")
  suspend fun searchPodcasts(
    @Path("libraryId") libraryId: String,
    @Query("q") request: String,
    @Query("limit") limit: Int,
  ): Response<PodcastSearchResponse>

  @GET("/api/items/{itemId}")
  suspend fun fetchLibraryItem(
    @Path("itemId") itemId: String,
  ): Response<BookResponse>

  @GET("/api/items/{itemId}")
  suspend fun fetchPodcastEpisode(
    @Path("itemId") itemId: String,
  ): Response<PodcastResponse>

  @GET("/api/authors/{authorId}?include=items")
  suspend fun fetchAuthorLibraryItems(
    @Path("authorId") authorId: String,
  ): Response<AuthorItemsResponse>

  @POST("/api/session/{itemId}/sync")
  suspend fun publishLibraryItemProgress(
    @Path("itemId") itemId: String,
    @Body syncProgressRequest: ProgressSyncRequest,
  ): Response<Unit>

  @POST("/api/items/{itemId}/play/{episodeId}")
  suspend fun startPodcastPlayback(
    @Path("itemId") itemId: String,
    @Path("episodeId") episodeId: String,
    @Body syncProgressRequest: PlaybackStartRequest,
  ): Response<PlaybackSessionResponse>

  @POST("/api/items/{itemId}/play")
  suspend fun startLibraryPlayback(
    @Path("itemId") itemId: String,
    @Body syncProgressRequest: PlaybackStartRequest,
  ): Response<PlaybackSessionResponse>

  @POST("login")
  @Headers("x-return-tokens: true")
  suspend fun login(
    @Body request: CredentialsLoginRequest,
  ): Response<LoggedUserResponse>

  @POST("auth/refresh")
  @Headers("x-return-tokens: true")
  suspend fun refreshToken(
    @Header("x-refresh-token") refreshToken: String,
  ): Response<LoggedUserResponse>

  @GET("/api/items/{itemId}/cover?raw=1")
  @Streaming
  suspend fun getItemCover(
    @Path("itemId") itemId: String,
  ): Response<ResponseBody>

  @GET("/api/items/{itemId}/cover")
  @Streaming
  suspend fun getItemCover(
    @Path("itemId") itemId: String,
    @Query("width") width: Int?,
  ): Response<ResponseBody>
}
