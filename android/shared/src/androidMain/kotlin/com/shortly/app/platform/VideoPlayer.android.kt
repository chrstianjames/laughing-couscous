package com.shortly.app.platform

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/** Shared on-disk cache for remote videos (200MB LRU). */
@OptIn(UnstableApi::class)
private object VideoCache {
    private const val SIZE = 200L * 1024 * 1024
    val simpleCache: SimpleCache by lazy {
        val ctx = AndroidPlatform.appContext
        val dir = File(ctx.cacheDir, "video_cache").apply { mkdirs() }
        SimpleCache(dir, LeastRecentlyUsedCacheEvictor(SIZE), StandaloneDatabaseProvider(ctx))
    }
}

@OptIn(UnstableApi::class)
actual class VideoPlayer actual constructor(actual val url: String, private val listener: VideoPlayerListener) {

    val exo: ExoPlayer

    private val exoListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
        override fun onRenderedFirstFrame() = listener.onFirstFrame()
        override fun onPlaybackStateChanged(playbackState: Int) {
            listener.onStateChanged(
                when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackState.Loading
                    Player.STATE_READY -> PlaybackState.Ready
                    Player.STATE_ENDED -> PlaybackState.Ended
                    else -> PlaybackState.Idle
                }
            )
        }
        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val detail = when {
                cause is HttpDataSource.InvalidResponseCodeException -> "Server returned HTTP ${cause.responseCode}"
                cause is HttpDataSource.HttpDataSourceException -> "Network error while loading video"
                error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                    "This device cannot decode the video format"
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                    "Video file is corrupt or unsupported"
                error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                    "Insecure (http) video URL blocked"
                else -> error.errorCodeName
            }
            android.util.Log.w("VideoPlayer", "Playback error for $url: ${error.errorCodeName}", error)
            listener.onError(detail)
        }
    }

    init {
        val ctx = AndroidPlatform.appContext
        val isRemote = url.startsWith("http", ignoreCase = true)
        val builder = ExoPlayer.Builder(ctx).setHandleAudioBecomingNoisy(true)
        if (isRemote) {
            val http = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setUserAgent("Shortly/1.0")
                .setDefaultRequestProperties(
                    AppGraph.tokenManager.getToken()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
                )
            val cached = CacheDataSource.Factory()
                .setCache(VideoCache.simpleCache)
                .setUpstreamDataSourceFactory(http)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, cached)))
        }
        exo = builder.build()
        exo.addListener(exoListener)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
    }

    actual fun play() { exo.playWhenReady = true }
    actual fun pause() { exo.playWhenReady = false }
    actual val isPlayWhenReady: Boolean get() = exo.playWhenReady
    actual fun setMuted(muted: Boolean) { exo.volume = if (muted) 0f else 1f }
    actual fun setLooping(loop: Boolean) { exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    actual fun seekTo(positionMs: Long) = exo.seekTo(positionMs)
    actual val currentPositionMs: Long get() = exo.currentPosition
    actual val durationMs: Long get() = exo.duration.coerceAtLeast(0)
    actual fun retry() { exo.prepare() }
    actual fun release() {
        exo.removeListener(exoListener)
        exo.release()
    }
}

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayerView(
    player: VideoPlayer,
    modifier: Modifier,
    zoom: Boolean,
    posterUrl: String?,
    showControls: Boolean
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = showControls
                if (showControls) controllerShowTimeoutMs = 1500
                resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                // Transparent shutter so the Compose-drawn poster shows through until the first frame.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
                this.player = player.exo
            }
        },
        update = { view ->
            if (view.player !== player.exo) view.player = player.exo
            view.useController = showControls
            view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        modifier = modifier
    )
}
