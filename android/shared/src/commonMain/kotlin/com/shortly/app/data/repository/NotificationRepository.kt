package com.shortly.app.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.shortly.app.platform.AppGraph
import com.shortly.app.platform.IoDispatcher
import com.shortly.app.data.model.NotificationItem
import com.shortly.app.data.model.User
import kotlinx.coroutines.withContext

class NotificationRepository private constructor() {
    private val api: ApiClient get() = AppGraph.api

    suspend fun getNotifications(page: Int): Result<Paginated<NotificationItem>> = withContext(IoDispatcher) {
        try {
            val resp = api.getNotifications(page)
            Result.success(Paginated(resp.notifications.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun markRead() = withContext(IoDispatcher) {
        try { api.markNotificationsRead() } catch (_: Exception) {}
    }

    companion object { val instance = NotificationRepository() }
}

class SocialRepository private constructor() {
    private val api: ApiClient get() = AppGraph.api

    suspend fun getUser(idOrUsername: String): Result<User> = withContext(IoDispatcher) {
        try {
            val resp = api.getUser(idOrUsername)
            Result.success(resp.user!!)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun follow(idOrUsername: String): Result<Boolean> = withContext(IoDispatcher) {
        try {
            val r = api.follow(idOrUsername)
            Result.success(r.following == true)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun unfollow(idOrUsername: String): Result<Boolean> = withContext(IoDispatcher) {
        try {
            val r = api.unfollow(idOrUsername)
            Result.success(r.following == true)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFollowers(idOrUsername: String, page: Int): Result<Paginated<User>> = withContext(IoDispatcher) {
        try {
            val r = api.getFollowers(idOrUsername, page)
            Result.success(Paginated(r.users.orEmpty(), r.page, r.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFollowing(idOrUsername: String, page: Int): Result<Paginated<User>> = withContext(IoDispatcher) {
        try {
            val r = api.getFollowing(idOrUsername, page)
            Result.success(Paginated(r.users.orEmpty(), r.page, r.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun block(idOrUsername: String): Result<Boolean> = withContext(IoDispatcher) {
        try { api.block(idOrUsername); Result.success(true) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun unblock(idOrUsername: String): Result<Boolean> = withContext(IoDispatcher) {
        try { api.unblock(idOrUsername); Result.success(false) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun search(q: String, type: String? = null, page: Int = 1): Result<SearchResults> = withContext(IoDispatcher) {
        try {
            val r = api.search(q, type, page)
            Result.success(SearchResults(
                users = r.users.orEmpty(),
                videos = r.videos.orEmpty(),
                hashtags = r.hashtags.orEmpty()
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun trendingHashtags(): Result<List<com.shortly.app.data.model.Hashtag>> = withContext(IoDispatcher) {
        try {
            val r = api.getTrendingHashtags()
            Result.success(r.hashtags.orEmpty())
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun hashtagVideos(tag: String, page: Int): Result<Paginated<com.shortly.app.data.model.Video>> = withContext(IoDispatcher) {
        try {
            val r = api.getHashtagVideos(tag, page)
            Result.success(Paginated(r.videos.orEmpty(), r.page, r.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun report(videoId: String? = null, userId: String? = null, reason: String): Result<Unit> = withContext(IoDispatcher) {
        try {
            val body = buildJsonObject {
                put("reason", reason)
                videoId?.let { put("video_id", it) }
                userId?.let { put("user_id", it) }
            }
            api.report(body)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    companion object { val instance = SocialRepository() }
}

data class SearchResults(
    val users: List<User>,
    val videos: List<com.shortly.app.data.model.Video>,
    val hashtags: List<com.shortly.app.data.model.Hashtag>
)
