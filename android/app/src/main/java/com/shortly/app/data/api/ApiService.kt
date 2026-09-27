package com.shortly.app.data.api

import com.google.gson.JsonObject
import com.shortly.app.data.model.ApiResponse
import com.shortly.app.data.model.AuthRequest
import com.shortly.app.data.model.AuthResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/register")
    suspend fun register(@Body req: AuthRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body req: AuthRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @GET("auth/me")
    suspend fun me(): ApiResponse<Unit>

    // Profile edit
    @POST("users/profile/edit")
    suspend fun editProfile(@Body body: JsonObject): ApiResponse<Unit>

    @Multipart
    @POST("users/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): ApiResponse<Unit>

    // Users
    @GET("users/{id}")
    suspend fun getUser(@Path("id") idOrUsername: String): ApiResponse<Unit>

    @GET("users/{id}/videos")
    suspend fun getUserVideos(
        @Path("id") idOrUsername: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Unit>

    @GET("users/search")
    suspend fun searchUsers(
        @Query("q") q: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Unit>

    // Follow
    @POST("users/{id}/follow")
    suspend fun follow(@Path("id") idOrUsername: String): ApiResponse<Unit>

    @DELETE("users/{id}/follow")
    suspend fun unfollow(@Path("id") idOrUsername: String): ApiResponse<Unit>

    @GET("users/{id}/followers")
    suspend fun getFollowers(
        @Path("id") idOrUsername: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 30
    ): ApiResponse<Unit>

    @GET("users/{id}/following")
    suspend fun getFollowing(
        @Path("id") idOrUsername: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 30
    ): ApiResponse<Unit>

    // Block
    @POST("users/{id}/block")
    suspend fun block(@Path("id") idOrUsername: String): ApiResponse<Unit>

    @DELETE("users/{id}/block")
    suspend fun unblock(@Path("id") idOrUsername: String): ApiResponse<Unit>

    // Videos
    @Multipart
    @POST("videos/upload")
    suspend fun uploadVideo(
        @Part file: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part thumbnail: MultipartBody.Part? = null
    ): ApiResponse<Unit>

    @GET("videos/{id}")
    suspend fun getVideo(@Path("id") id: String): ApiResponse<Unit>

    @DELETE("videos/{id}")
    suspend fun deleteVideo(@Path("id") id: String): ApiResponse<Unit>

    @POST("videos/{id}/view")
    suspend fun viewVideo(@Path("id") id: String): ApiResponse<Unit>

    // Feed
    @GET("feed/for-you")
    suspend fun getForYouFeed(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 10
    ): ApiResponse<Unit>

    @GET("feed/following")
    suspend fun getFollowingFeed(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 10
    ): ApiResponse<Unit>

    // Likes
    @POST("videos/{id}/like")
    suspend fun likeVideo(@Path("id") id: String): ApiResponse<Unit>

    @DELETE("videos/{id}/like")
    suspend fun unlikeVideo(@Path("id") id: String): ApiResponse<Unit>

    // Saves
    @POST("videos/{id}/save")
    suspend fun saveVideo(@Path("id") id: String): ApiResponse<Unit>

    @DELETE("videos/{id}/save")
    suspend fun unsaveVideo(@Path("id") id: String): ApiResponse<Unit>

    @GET("users/me/saved")
    suspend fun getSavedVideos(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Unit>

    // Comments
    @GET("videos/{id}/comments")
    suspend fun getComments(
        @Path("id") videoId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Unit>

    @POST("videos/{id}/comments")
    suspend fun addComment(@Path("id") videoId: String, @Body body: JsonObject): ApiResponse<Unit>

    @POST("comments/{id}/like")
    suspend fun likeComment(@Path("id") id: String): ApiResponse<Unit>

    @DELETE("comments/{id}/like")
    suspend fun unlikeComment(@Path("id") id: String): ApiResponse<Unit>

    @GET("comments/{id}/replies")
    suspend fun getCommentReplies(
        @Path("id") commentId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Unit>

    @DELETE("comments/{id}")
    suspend fun deleteComment(@Path("id") id: String): ApiResponse<Unit>

    // Notifications
    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 30
    ): ApiResponse<Unit>

    @POST("notifications/read")
    suspend fun markNotificationsRead(): ApiResponse<Unit>

    // Search & hashtags
    @GET("search")
    suspend fun search(
        @Query("q") q: String,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Unit>

    @GET("hashtags/trending")
    suspend fun getTrendingHashtags(): ApiResponse<Unit>

    @GET("hashtags/{tag}/videos")
    suspend fun getHashtagVideos(
        @Path("tag") tag: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int = 20
    ): ApiResponse<Unit>

    // Report
    @POST("report")
    suspend fun report(@Body body: JsonObject): ApiResponse<Unit>
}
