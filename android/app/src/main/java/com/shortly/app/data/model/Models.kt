package com.shortly.app.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val username: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val bio: String = "",
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("is_following") val isFollowing: Boolean? = null,
    @SerializedName("is_blocked") val isBlocked: Boolean? = null,
    val email: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class Video(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val author: User? = null,
    @SerializedName("video_url") val videoUrl: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val duration: Float = 0f,
    val views: Int = 0,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    @SerializedName("saves_count") val savesCount: Int = 0,
    @SerializedName("is_liked") val isLiked: Boolean = false,
    @SerializedName("is_saved") val isSaved: Boolean = false,
    @SerializedName("is_following_author") val isFollowingAuthor: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class Comment(
    val id: String,
    @SerializedName("video_id") val videoId: String,
    @SerializedName("parent_id") val parentId: String? = null,
    val user: User? = null,
    val text: String,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("is_liked") val isLiked: Boolean = false,
    @SerializedName("reply_count") val replyCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class NotificationItem(
    val id: String,
    val type: String,
    @SerializedName("from_user") val fromUser: User? = null,
    val video: Video? = null,
    val read: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class Hashtag(
    val tag: String,
    @SerializedName("videos_count") val videosCount: Int = 0
)

// Request/response wrappers
data class AuthRequest(
    val login: String? = null,
    val username: String? = null,
    val email: String? = null,
    val password: String,
    @SerializedName("display_name") val displayName: String? = null
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class ApiResponse<T>(
    val error: Boolean? = null,
    val message: String? = null,
    // Generic fields - we parse based on endpoint
    val user: User? = null,
    val video: Video? = null,
    val videos: List<Video>? = null,
    val comment: Comment? = null,
    val comments: List<Comment>? = null,
    val replies: List<Comment>? = null,
    val users: List<User>? = null,
    val notifications: List<NotificationItem>? = null,
    val hashtags: List<Hashtag>? = null,
    val liked: Boolean? = null,
    val saved: Boolean? = null,
    val following: Boolean? = null,
    val success: Boolean? = null,
    @SerializedName("likes_count") val likesCount: Int? = null,
    @SerializedName("saves_count") val savesCount: Int? = null,
    val page: Int = 1,
    @SerializedName("has_more") val hasMore: Boolean = false,
    val total: Int = 0,
    val tag: String? = null
)

data class SimpleResponse(
    val error: Boolean? = null,
    val message: String? = null,
    val success: Boolean? = null
)
