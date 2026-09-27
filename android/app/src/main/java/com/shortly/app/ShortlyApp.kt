package com.shortly.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.shortly.app.data.api.ApiService
import com.shortly.app.data.prefs.TokenManager
import com.shortly.app.data.repository.NotificationRepository
import com.shortly.app.data.repository.VideoCache
import com.shortly.app.util.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thrown for any non-2xx API response. [message] is the server's JSON
 * `message` field when present, so the UI shows e.g. "Invalid credentials"
 * instead of a bare "HTTP 401".
 */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Converts non-2xx responses into [ApiException] carrying the server's
 * human-readable error message.
 */
class ApiErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response

        val code = response.code
        // peekBody so the body remains readable downstream
        val raw = try {
            response.peekBody(64 * 1024).string()
        } catch (_: Exception) {
            ""
        }
        val serverMessage = parseMessage(raw)
        val text = when {
            !serverMessage.isNullOrBlank() -> serverMessage
            code == 401 -> "Invalid credentials"
            code == 404 -> "Not found"
            code == 429 -> "Too many requests. Try again later."
            code >= 500 -> "Server error ($code). Please try again later."
            else -> "Request failed ($code)"
        }
        response.close()
        throw ApiException(code, text)
    }

    private fun parseMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val msg = obj.optString("message", "")
            if (msg.isNotBlank()) msg
            else obj.optString("error", "").takeIf { it.isNotBlank() && it != "true" && it != "false" }
        } catch (_: Exception) {
            null
        }
    }
}

class ShortlyApp : Application(), ImageLoaderFactory {

    lateinit var apiService: ApiService
    lateinit var tokenManager: TokenManager
    lateinit var videoCache: VideoCache

    override fun onCreate() {
        super.onCreate()
        instance = this

        tokenManager = TokenManager(this)
        videoCache = VideoCache(this)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                tokenManager.getToken()?.let {
                    req.addHeader("Authorization", "Bearer $it")
                }
                req.build().let { chain.proceed(it) }
            }
            // Must come after the auth interceptor so it sees the final response
            .addInterceptor(ApiErrorInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.API_BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "notifications",
                "Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Likes, follows, comments" }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                        val token = getSharedPreferences("shortly_sync", MODE_PRIVATE).getString("token", null)
                        token?.let {
                            req.addHeader("Authorization", "Bearer $it")
                        }
                        chain.proceed(req.build())
                    }
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: ShortlyApp
            private set
    }
}
