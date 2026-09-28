package com.shortly.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.shortly.app.platform.AndroidPlatform

/**
 * Android-only bootstrap. All app logic lives in the shared module; this just
 * wires the platform context, notification channels and the image cache.
 */
class ShortlyApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "notifications",
                "Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Likes, follows, comments" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            .crossfade(true)
            .build()
}
