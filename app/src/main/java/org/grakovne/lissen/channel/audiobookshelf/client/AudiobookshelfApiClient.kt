package org.grakovne.lissen.channel.audiobookshelf.client

import org.grakovne.lissen.channel.audiobookshelf.model.AuthorResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibraryItemIdResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LoginRequest
import org.grakovne.lissen.channel.audiobookshelf.model.LoginResponse
import org.grakovne.lissen.channel.audiobookshelf.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.model.PersonalizedFeedResponse
import org.grakovne.lissen.channel.audiobookshelf.model.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.model.SyncProgressRequest
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

    @GET("api/libraries/{libraryId}/items?sort=media.metadata.title&minified=1")
    suspend fun fetchLibraryItems(
        @Path("libraryId") libraryId: String,
        @Query("limit") pageSize: Int,
        @Query("page") pageNumber: Int
    ): Response<LibraryItemsResponse>

    @GET("api/libraries/{libraryId}/search")
    suspend fun searchLibraryItems(
        @Path("libraryId") libraryId: String,
        @Query("q") request: String,
        @Query("limit") limit: Int
    ): Response<LibrarySearchResponse>

    @GET("/api/items/{itemId}")
    suspend fun fetchLibraryItem(
        @Path("itemId") itemId: String
    ): Response<LibraryItemIdResponse>

    @GET("/api/authors/{authorId}?include=items")
    suspend fun fetchAuthorLibraryItems(
        @Path("authorId") authorId: String
    ): Response<AuthorResponse>

    @POST("/api/session/{itemId}/sync")
    suspend fun publishLibraryItemProgress(
        @Path("itemId") itemId: String,
        @Body syncProgressRequest: SyncProgressRequest
    ): Response<Unit>

    @POST("/api/items/{itemId}/play")
    suspend fun startPlayback(
        @Path("itemId") itemId: String,
        @Body syncProgressRequest: org.grakovne.lissen.channel.audiobookshelf.model.StartPlaybackRequest
    ): Response<PlaybackSessionResponse>

    @POST("/api/session/{sessionId}/close")
    suspend fun stopPlayback(
        @Path("sessionId") sessionId: String
    ): Response<Unit>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
