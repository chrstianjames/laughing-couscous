package com.shortly.app.data.api

import com.shortly.app.data.model.ApiResponse
import com.shortly.app.data.model.AuthRequest
import com.shortly.app.data.model.AuthResponse
import com.shortly.app.platform.PickedFile
import com.shortly.app.platform.createPlatformHttpClient
import com.shortly.app.platform.platformMultipartUpload
import com.shortly.app.util.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Thrown for any non-2xx API response. [message] is the server's JSON
 * `message` field when present, so the UI shows e.g. "Invalid credentials"
 * instead of a bare "HTTP 401".
 */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Multiplatform replacement for the Retrofit `ApiService`: same endpoints,
 * same response shapes, built on Ktor + kotlinx.serialization so it runs on
 * Android and in the browser (Kotlin/Wasm).
 */
class ApiClient(private val tokenProvider: () -> String?) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client: HttpClient = createPlatformHttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
        defaultRequest {
            tokenProvider()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.Accept, "application/json")
        }
    }

    private fun url(path: String) = Constants.API_BASE + path.trimStart('/')

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {}
    ): T {
        val response: HttpResponse = client.request(url(path)) {
            this.method = method
            block()
        }
        if (response.status.value !in 200..299) throw errorFor(response.status.value, response.bodyAsText())
        return response.body()
    }

    private fun errorFor(code: Int, raw: String): ApiException {
        val serverMessage = parseMessage(raw)
        val text = when {
            !serverMessage.isNullOrBlank() -> serverMessage
            code == 401 -> "Invalid credentials"
            code == 404 -> "Not found"
            code == 429 -> "Too many requests. Try again later."
            code >= 500 -> "Server error ($code). Please try again later."
            else -> "Request failed ($code)"
        }
        return ApiException(code, text)
    }

    fun parseMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return null
            (obj["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend inline fun <reified T> get(path: String, crossinline block: HttpRequestBuilder.() -> Unit = {}): T =
        call(HttpMethod.Get, path, block)

    private suspend inline fun <reified T> post(path: String, body: Any? = null): T =
        call(HttpMethod.Post, path) {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend inline fun <reified T> delete(path: String): T = call(HttpMethod.Delete, path)

    private fun p(s: String) = s.encodeURLPathPart()

    // ---------------------------------------------------------------- Auth
    suspend fun register(req: AuthRequest): AuthResponse = post("auth/register", req)
    suspend fun login(req: AuthRequest): AuthResponse = post("auth/login", req)
    suspend fun logout(): ApiResponse = post("auth/logout")
    suspend fun me(): ApiResponse = get("auth/me")

    // ---------------------------------------------------------------- Profile
    suspend fun editProfile(body: JsonObject): ApiResponse = post("users/profile/edit", body)

    suspend fun uploadAvatar(file: PickedFile): ApiResponse =
        multipart("users/avatar", emptyMap(), listOf("file" to file))

    // ---------------------------------------------------------------- Users
    suspend fun getUser(idOrUsername: String): ApiResponse = get("users/${p(idOrUsername)}")
    suspend fun getUserVideos(idOrUsername: String, page: Int, limit: Int = 20): ApiResponse =
        get("users/${p(idOrUsername)}/videos") { parameter("page", page); parameter("limit", limit) }
    suspend fun searchUsers(q: String, page: Int = 1, limit: Int = 20): ApiResponse =
        get("users/search") { parameter("q", q); parameter("page", page); parameter("limit", limit) }

    // ---------------------------------------------------------------- Follow / block
    suspend fun follow(idOrUsername: String): ApiResponse = post("users/${p(idOrUsername)}/follow")
    suspend fun unfollow(idOrUsername: String): ApiResponse = delete("users/${p(idOrUsername)}/follow")
    suspend fun getFollowers(idOrUsername: String, page: Int, limit: Int = 30): ApiResponse =
        get("users/${p(idOrUsername)}/followers") { parameter("page", page); parameter("limit", limit) }
    suspend fun getFollowing(idOrUsername: String, page: Int, limit: Int = 30): ApiResponse =
        get("users/${p(idOrUsername)}/following") { parameter("page", page); parameter("limit", limit) }
    suspend fun block(idOrUsername: String): ApiResponse = post("users/${p(idOrUsername)}/block")
    suspend fun unblock(idOrUsername: String): ApiResponse = delete("users/${p(idOrUsername)}/block")

    // ---------------------------------------------------------------- Videos
    suspend fun uploadVideo(file: PickedFile, caption: String, duration: Float, thumbnail: PickedFile?): ApiResponse {
        val files = buildList {
            add("file" to file)
            thumbnail?.let { add("thumbnail" to it) }
        }
        return multipart("videos/upload", mapOf("caption" to caption, "duration" to duration.toString()), files)
    }
    suspend fun getVideo(id: String): ApiResponse = get("videos/${p(id)}")
    suspend fun deleteVideo(id: String): ApiResponse = delete("videos/${p(id)}")
    suspend fun viewVideo(id: String): ApiResponse = post("videos/${p(id)}/view")

    // ---------------------------------------------------------------- Feed
    suspend fun getForYouFeed(page: Int, limit: Int = 10): ApiResponse =
        get("feed/for-you") { parameter("page", page); parameter("limit", limit) }
    suspend fun getFollowingFeed(page: Int, limit: Int = 10): ApiResponse =
        get("feed/following") { parameter("page", page); parameter("limit", limit) }

    // ---------------------------------------------------------------- Likes / saves
    suspend fun likeVideo(id: String): ApiResponse = post("videos/${p(id)}/like")
    suspend fun unlikeVideo(id: String): ApiResponse = delete("videos/${p(id)}/like")
    suspend fun saveVideo(id: String): ApiResponse = post("videos/${p(id)}/save")
    suspend fun unsaveVideo(id: String): ApiResponse = delete("videos/${p(id)}/save")
    suspend fun getSavedVideos(page: Int, limit: Int = 20): ApiResponse =
        get("users/me/saved") { parameter("page", page); parameter("limit", limit) }

    // ---------------------------------------------------------------- Comments
    suspend fun getComments(videoId: String, page: Int, limit: Int = 20): ApiResponse =
        get("videos/${p(videoId)}/comments") { parameter("page", page); parameter("limit", limit) }
    suspend fun addComment(videoId: String, body: JsonObject): ApiResponse = post("videos/${p(videoId)}/comments", body)
    suspend fun likeComment(id: String): ApiResponse = post("comments/${p(id)}/like")
    suspend fun unlikeComment(id: String): ApiResponse = delete("comments/${p(id)}/like")
    suspend fun getCommentReplies(commentId: String, page: Int, limit: Int = 20): ApiResponse =
        get("comments/${p(commentId)}/replies") { parameter("page", page); parameter("limit", limit) }
    suspend fun deleteComment(id: String): ApiResponse = delete("comments/${p(id)}")

    // ---------------------------------------------------------------- Notifications
    suspend fun getNotifications(page: Int, limit: Int = 30): ApiResponse =
        get("notifications") { parameter("page", page); parameter("limit", limit) }
    suspend fun markNotificationsRead(): ApiResponse = post("notifications/read")

    // ---------------------------------------------------------------- Search / hashtags
    suspend fun search(q: String, type: String? = null, page: Int = 1, limit: Int = 20): ApiResponse =
        get("search") {
            parameter("q", q); type?.let { parameter("type", it) }
            parameter("page", page); parameter("limit", limit)
        }
    suspend fun getTrendingHashtags(): ApiResponse = get("hashtags/trending")
    suspend fun getHashtagVideos(tag: String, page: Int, limit: Int = 20): ApiResponse =
        get("hashtags/${p(tag)}/videos") { parameter("page", page); parameter("limit", limit) }

    // ---------------------------------------------------------------- Report
    suspend fun report(body: JsonObject): ApiResponse = post("report", body)

    // ---------------------------------------------------------------- helpers
    private suspend fun multipart(path: String, fields: Map<String, String>, files: List<Pair<String, PickedFile>>): ApiResponse {
        val r = platformMultipartUpload(url(path), tokenProvider(), fields, files)
        if (r.status !in 200..299) throw errorFor(r.status, r.body)
        return json.decodeFromString(ApiResponse.serializer(), r.body)
    }

    companion object {
        fun jsonObject(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(builder)
    }
}
