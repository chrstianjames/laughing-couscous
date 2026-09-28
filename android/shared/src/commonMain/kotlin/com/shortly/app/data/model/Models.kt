package com.shortly.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String = "",
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("is_following") val isFollowing: Boolean? = null,
    @SerialName("is_blocked") val isBlocked: Boolean? = null,
    val email: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Video(
    val id: String,
    @SerialName("user_id") val userId: String,
    val author: User? = null,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val duration: Float = 0f,
    val views: Int = 0,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    @SerialName("saves_count") val savesCount: Int = 0,
    @SerialName("is_liked") val isLiked: Boolean = false,
    @SerialName("is_saved") val isSaved: Boolean = false,
    @SerialName("is_following_author") val isFollowingAuthor: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Comment(
    val id: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val user: User? = null,
    val text: String,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("is_liked") val isLiked: Boolean = false,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class NotificationItem(
    val id: String,
    val type: String,
    @SerialName("from_user") val fromUser: User? = null,
    val video: Video? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Hashtag(
    val tag: String,
    @SerialName("videos_count") val videosCount: Int = 0
)

// Request/response wrappers
@Serializable
data class AuthRequest(
    val login: String? = null,
    val username: String? = null,
    val email: String? = null,
    val password: String,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: User
)

@Serializable
data class ApiResponse(
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
    @SerialName("likes_count") val likesCount: Int? = null,
    @SerialName("saves_count") val savesCount: Int? = null,
    val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false,
    val total: Int = 0,
    val tag: String? = null
)

@Serializable
data class SimpleResponse(
    val error: Boolean? = null,
    val message: String? = null,
    val success: Boolean? = null
)
