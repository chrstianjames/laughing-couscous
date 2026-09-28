package com.shortly.app.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.shortly.app.platform.AppGraph
import com.shortly.app.platform.IoDispatcher
import com.shortly.app.platform.PickedFile
import com.shortly.app.data.api.ApiClient
import com.shortly.app.data.model.*
import kotlinx.coroutines.withContext

class VideoRepository private constructor() {
    private val api: ApiClient get() = AppGraph.api

    suspend fun getForYou(page: Int): Result<Paginated<Video>> = withContext(IoDispatcher) {
        try {
            val resp = api.getForYouFeed(page)
            Result.success(Paginated(
                items = resp.videos.orEmpty(),
                page = resp.page,
                hasMore = resp.hasMore
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFollowing(page: Int): Result<Paginated<Video>> = withContext(IoDispatcher) {
        try {
            val resp = api.getFollowingFeed(page)
            Result.success(Paginated(
                items = resp.videos.orEmpty(),
                page = resp.page,
                hasMore = resp.hasMore
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getUserVideos(username: String, page: Int): Result<Paginated<Video>> = withContext(IoDispatcher) {
        try {
            val resp = api.getUserVideos(username, page)
            Result.success(Paginated(resp.videos.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getSaved(page: Int): Result<Paginated<Video>> = withContext(IoDispatcher) {
        try {
            val resp = api.getSavedVideos(page)
            Result.success(Paginated(resp.videos.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getVideo(id: String): Result<Video> = withContext(IoDispatcher) {
        try {
            val resp = api.getVideo(id)
            Result.success(resp.video!!)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleLike(video: Video): Result<Video> = withContext(IoDispatcher) {
        try {
            val resp = if (video.isLiked) api.unlikeVideo(video.id) else api.likeVideo(video.id)
            val updated = video.copy(
                isLiked = resp.liked ?: !video.isLiked,
                likesCount = resp.likesCount ?: (if (video.isLiked) video.likesCount - 1 else video.likesCount + 1)
            )
            Result.success(updated)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleSave(video: Video): Result<Video> = withContext(IoDispatcher) {
        try {
            val resp = if (video.isSaved) api.unsaveVideo(video.id) else api.saveVideo(video.id)
            val updated = video.copy(
                isSaved = resp.saved ?: !video.isSaved,
                savesCount = resp.savesCount ?: (if (video.isSaved) video.savesCount - 1 else video.savesCount + 1)
            )
            Result.success(updated)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleFollow(video: Video): Result<Video> = withContext(IoDispatcher) {
        try {
            val uid = video.author?.id ?: return@withContext Result.failure(Exception("No author"))
            val resp = if (video.isFollowingAuthor) api.unfollow(uid) else api.follow(uid)
            Result.success(video.copy(isFollowingAuthor = resp.following ?: !video.isFollowingAuthor))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun viewVideo(id: String) = withContext(IoDispatcher) {
        try { api.viewVideo(id) } catch (_: Exception) {}
    }

    suspend fun deleteVideo(id: String): Result<Unit> = withContext(IoDispatcher) {
        try {
            api.deleteVideo(id)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadVideo(
        file: PickedFile,
        caption: String,
        duration: Float,
        thumbnail: PickedFile? = null
    ): Result<Video> = withContext(IoDispatcher) {
        try {
            val resp = api.uploadVideo(file, caption, duration, thumbnail?.takeIf { it.size > 0 })
            val video = resp.video ?: throw IllegalStateException(resp.message ?: "Server did not return the uploaded video")
            Result.success(video)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getComments(videoId: String, page: Int, limit: Int = 20): Result<Paginated<Comment>> = withContext(IoDispatcher) {
        try {
            val resp = api.getComments(videoId, page, limit)
            Result.success(Paginated(resp.comments.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getReplies(commentId: String, page: Int, limit: Int = 20): Result<Paginated<Comment>> = withContext(IoDispatcher) {
        try {
            val resp = api.getCommentReplies(commentId, page, limit)
            Result.success(Paginated(resp.replies.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addComment(videoId: String, text: String, parentId: String? = null): Result<Comment> = withContext(IoDispatcher) {
        try {
            val body = buildJsonObject {
                put("text", text)
                parentId?.let { put("parent_id", it) }
            }
            val resp = api.addComment(videoId, body)
            Result.success(resp.comment!!)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleLikeComment(comment: Comment): Result<Comment> = withContext(IoDispatcher) {
        try {
            val resp = if (comment.isLiked) api.unlikeComment(comment.id) else api.likeComment(comment.id)
            Result.success(comment.copy(
                isLiked = resp.liked ?: !comment.isLiked,
                likesCount = resp.likesCount ?: (if (comment.isLiked) comment.likesCount - 1 else comment.likesCount + 1)
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteComment(id: String): Result<Unit> = withContext(IoDispatcher) {
        try { api.deleteComment(id); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    companion object {
        val instance = VideoRepository()
    }
}

data class Paginated<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean
)
