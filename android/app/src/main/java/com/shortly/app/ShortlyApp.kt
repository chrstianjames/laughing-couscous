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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

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
