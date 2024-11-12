package org.grakovne.lissen.channel.audiobookshelf.common.client

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
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastSearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AudiobookshelfApiClient {

    @GET("/api/libraries")
    suspend fun fetchLibraries(): Response<LibraryResponse>

    @GET("/api/libraries/{libraryId}/personalized")
    suspend fun fetchPersonalizedFeed(
        @Path("libraryId") libraryId: String
    ): Response<List<PersonalizedFeedResponse>>

    @GET("/api/me/progress/{itemId}")
    suspend fun fetchLibraryItemProgress(
        @Path("itemId") itemId: String
    ): Response<MediaProgressResponse>

    @POST("/api/authorize")
    suspend fun fetchConnectionInfo(): Response<ConnectionInfoResponse>

    @POST("/api/authorize")
    suspend fun fetchUserInfo(): Response<UserInfoResponse>

    @GET("api/libraries/{libraryId}/items?sort=media.metadata.title&minified=1")
    suspend fun fetchLibraryItems(
        @Path("libraryId") libraryId: String,
        @Query("limit") pageSize: Int,
        @Query("page") pageNumber: Int
    ): Response<LibraryItemsResponse>

    @GET("api/libraries/{libraryId}/items?sort=mtimeMs&desc=1")
    suspend fun fetchPodcastItems(
        @Path("libraryId") libraryId: String,
        @Query("limit") pageSize: Int,
        @Query("page") pageNumber: Int
    ): Response<PodcastItemsResponse>

    @GET("api/libraries/{libraryId}/search")
    suspend fun searchLibraryItems(
        @Path("libraryId") libraryId: String,
        @Query("q") request: String,
        @Query("limit") limit: Int
    ): Response<LibrarySearchResponse>

    @GET("api/libraries/{libraryId}/search")
    suspend fun searchPodcasts(
        @Path("libraryId") libraryId: String,
        @Query("q") request: String,
        @Query("limit") limit: Int
    ): Response<PodcastSearchResponse>

    @GET("/api/items/{itemId}")
    suspend fun fetchLibraryItem(
        @Path("itemId") itemId: String
    ): Response<BookResponse>

    @GET("/api/items/{itemId}")
    suspend fun fetchPodcastEpisode(
        @Path("itemId") itemId: String
    ): Response<PodcastResponse>

    @GET("/api/authors/{authorId}?include=items")
    suspend fun fetchAuthorLibraryItems(
        @Path("authorId") authorId: String
    ): Response<AuthorItemsResponse>

    @POST("/api/session/{itemId}/sync")
    suspend fun publishLibraryItemProgress(
        @Path("itemId") itemId: String,
        @Body syncProgressRequest: ProgressSyncRequest
    ): Response<Unit>

    @POST("/api/items/{itemId}/play/{episodeId}")
    suspend fun startPodcastPlayback(
        @Path("itemId") itemId: String,
        @Path("episodeId") episodeId: String,
        @Body syncProgressRequest: PlaybackStartRequest
    ): Response<PlaybackSessionResponse>

    @POST("/api/items/{itemId}/play")
    suspend fun startLibraryPlayback(
        @Path("itemId") itemId: String,
        @Body syncProgressRequest: PlaybackStartRequest
    ): Response<PlaybackSessionResponse>

    @POST("/api/session/{sessionId}/close")
    suspend fun stopPlayback(
        @Path("sessionId") sessionId: String
    ): Response<Unit>

    @POST("login")
    suspend fun login(@Body request: CredentialsLoginRequest): Response<LoggedUserResponse>
}
