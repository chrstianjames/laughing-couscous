package com.shortly.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.shortly.app.ShortlyApp
import com.shortly.app.data.api.ApiService
import com.shortly.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class VideoRepository private constructor() {
    private val api: ApiService get() = ShortlyApp.instance.apiService

    suspend fun getForYou(page: Int): Result<Paginated<Video>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getForYouFeed(page)
            Result.success(Paginated(
                items = resp.videos.orEmpty(),
                page = resp.page,
                hasMore = resp.hasMore
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFollowing(page: Int): Result<Paginated<Video>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getFollowingFeed(page)
            Result.success(Paginated(
                items = resp.videos.orEmpty(),
                page = resp.page,
                hasMore = resp.hasMore
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getUserVideos(username: String, page: Int): Result<Paginated<Video>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getUserVideos(username, page)
            Result.success(Paginated(resp.videos.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getSaved(page: Int): Result<Paginated<Video>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getSavedVideos(page)
            Result.success(Paginated(resp.videos.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getVideo(id: String): Result<Video> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getVideo(id)
            Result.success(resp.video!!)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleLike(video: Video): Result<Video> = withContext(Dispatchers.IO) {
        try {
            val resp = if (video.isLiked) api.unlikeVideo(video.id) else api.likeVideo(video.id)
            val updated = video.copy(
                isLiked = resp.liked ?: !video.isLiked,
                likesCount = resp.likesCount ?: (if (video.isLiked) video.likesCount - 1 else video.likesCount + 1)
            )
            Result.success(updated)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleSave(video: Video): Result<Video> = withContext(Dispatchers.IO) {
        try {
            val resp = if (video.isSaved) api.unsaveVideo(video.id) else api.saveVideo(video.id)
            val updated = video.copy(
                isSaved = resp.saved ?: !video.isSaved,
                savesCount = resp.savesCount ?: (if (video.isSaved) video.savesCount - 1 else video.savesCount + 1)
            )
            Result.success(updated)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleFollow(video: Video): Result<Video> = withContext(Dispatchers.IO) {
        try {
            val uid = video.author?.id ?: return@withContext Result.failure(Exception("No author"))
            val resp = if (video.isFollowingAuthor) api.unfollow(uid) else api.follow(uid)
            Result.success(video.copy(isFollowingAuthor = resp.following ?: !video.isFollowingAuthor))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun viewVideo(id: String) = withContext(Dispatchers.IO) {
        try { api.viewVideo(id) } catch (_: Exception) {}
    }

    suspend fun deleteVideo(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api.deleteVideo(id)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadVideo(file: File, caption: String, duration: Float): Result<Video> = withContext(Dispatchers.IO) {
        try {
            val reqFile = file.asRequestBody("video/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, reqFile)
            val captionBody = caption.toRequestBody("text/plain".toMediaTypeOrNull())
            val durationBody = duration.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val resp = api.uploadVideo(part, captionBody, durationBody)
            Result.success(resp.video!!)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getComments(videoId: String, page: Int, limit: Int = 20): Result<Paginated<Comment>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getComments(videoId, page, limit)
            Result.success(Paginated(resp.comments.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getReplies(commentId: String, page: Int, limit: Int = 20): Result<Paginated<Comment>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getCommentReplies(commentId, page, limit)
            Result.success(Paginated(resp.replies.orEmpty(), resp.page, resp.hasMore))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addComment(videoId: String, text: String, parentId: String? = null): Result<Comment> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("text", text)
                parentId?.let { addProperty("parent_id", it) }
            }
            val resp = api.addComment(videoId, body)
            Result.success(resp.comment!!)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleLikeComment(comment: Comment): Result<Comment> = withContext(Dispatchers.IO) {
        try {
            val resp = if (comment.isLiked) api.unlikeComment(comment.id) else api.likeComment(comment.id)
            Result.success(comment.copy(
                isLiked = resp.liked ?: !comment.isLiked,
                likesCount = resp.likesCount ?: (if (comment.isLiked) comment.likesCount - 1 else comment.likesCount + 1)
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteComment(id: String): Result<Unit> = withContext(Dispatchers.IO) {
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
