package com.shortly.app.data.repository

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.shortly.app.util.Constants
import java.io.File

@UnstableApi
class VideoCache(context: Context) {

    val simpleCache: SimpleCache by lazy {
        val cacheDir = File(context.cacheDir, "video_cache").apply { mkdirs() }
        val evictor = LeastRecentlyUsedCacheEvictor(Constants.VIDEO_CACHE_SIZE)
        val dbProvider = StandaloneDatabaseProvider(context)
        SimpleCache(cacheDir, evictor, dbProvider)
    }
}
