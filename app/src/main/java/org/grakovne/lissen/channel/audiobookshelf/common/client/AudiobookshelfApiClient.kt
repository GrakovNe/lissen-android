package org.grakovne.lissen.channel.audiobookshelf.common.client

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.PodcastResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.StartPlaybackRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.SyncProgressRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.AuthorResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.LoginRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.LoginResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.PersonalizedFeedResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.library.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.library.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.library.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.podcast.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.podcast.PodcastSearchResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.podcast.UserInfoResponse
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
    ): Response<AuthorResponse>

    @POST("/api/session/{itemId}/sync")
    suspend fun publishLibraryItemProgress(
        @Path("itemId") itemId: String,
        @Body syncProgressRequest: SyncProgressRequest
    ): Response<Unit>

    @POST("/api/items/{itemId}/play/{episodeId}")
    suspend fun startPodcastPlayback(
        @Path("itemId") itemId: String,
        @Path("episodeId") episodeId: String,
        @Body syncProgressRequest: StartPlaybackRequest
    ): Response<PlaybackSessionResponse>

    @POST("/api/items/{itemId}/play")
    suspend fun startLibraryPlayback(
        @Path("itemId") itemId: String,
        @Body syncProgressRequest: StartPlaybackRequest
    ): Response<PlaybackSessionResponse>

    @POST("/api/session/{sessionId}/close")
    suspend fun stopPlayback(
        @Path("sessionId") sessionId: String
    ): Response<Unit>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
