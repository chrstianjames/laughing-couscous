package com.shortly.app.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.shortly.app.ShortlyApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
object PlayerPool {

    private var currentPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted
    private val _progress = MutableStateFlow(0L to 0L)
    val progress: StateFlow<Pair<Long, Long>> = _progress
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState

    private val listeners = mutableListOf<() -> Unit>()

    sealed class PlayerState {
        object Idle : PlayerState()
        object Loading : PlayerState()
        object Ready : PlayerState()
        data class Error(val message: String) : PlayerState()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }
        override fun onPlaybackStateChanged(state: Int) {
            _playerState.value = when (state) {
                Player.STATE_IDLE -> PlayerState.Idle
                Player.STATE_BUFFERING -> PlayerState.Loading
                Player.STATE_READY -> PlayerState.Ready
                Player.STATE_ENDED -> PlayerState.Ready
                else -> PlayerState.Idle
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val detail = when {
                cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException ->
                    "Server returned HTTP ${cause.responseCode}"
                cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException ->
                    "Network error while loading video"
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
            android.util.Log.w("PlayerPool", "Playback error for $currentUrl: ${error.errorCodeName}", error)
            _playerState.value = PlayerState.Error(detail)
        }
    }

    @Synchronized
    fun acquire(context: Context, url: String, playWhenReady: Boolean = true): ExoPlayer {
        val appContext = context.applicationContext
        if (currentPlayer != null && currentUrl == url) {
            currentPlayer?.playWhenReady = playWhenReady
            attachProgressListener()
            return currentPlayer!!
        }
        release()

        val cache = ShortlyApp.instance.videoCache.simpleCache

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            // Send the session token: harmless for public files and required
            // if the server ever gates /stream/ behind auth.
            .setDefaultRequestProperties(
                ShortlyApp.instance.tokenManager.getToken()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
            )
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setUserAgent("Shortly/1.0")

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = DefaultDataSource.Factory(appContext, cacheDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()

        player.addListener(listener)
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = if (_isMuted.value) 0f else 1f

        currentPlayer = player
        currentUrl = url
        attachProgressListener()
        return player
    }

    @Synchronized
    fun release() {
        removeProgressListener()
        currentPlayer?.removeListener(listener)
        currentPlayer?.release()
        currentPlayer = null
        currentUrl = null
        _isPlaying.value = false
        _progress.value = 0L to 0L
        _playerState.value = PlayerState.Idle
    }

    fun pause() {
        currentPlayer?.playWhenReady = false
    }

    /** Re-prepare the current item after a playback error. */
    fun retry() {
        currentPlayer?.let {
            _playerState.value = PlayerState.Loading
            it.prepare()
            it.playWhenReady = true
        }
    }

    fun resume() {
        currentPlayer?.playWhenReady = true
    }

    fun togglePlay() {
        currentPlayer?.let {
            it.playWhenReady = !it.playWhenReady
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        currentPlayer?.volume = if (_isMuted.value) 0f else 1f
    }

    fun seekTo(ms: Long) {
        currentPlayer?.seekTo(ms)
    }

    private var progressUpdater: Runnable? = null
    private fun attachProgressListener() {
        removeProgressListener()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        progressUpdater = object : Runnable {
            override fun run() {
                currentPlayer?.let { p ->
                    if (p.duration > 0) {
                        _progress.value = p.currentPosition to p.duration
                    }
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(progressUpdater!!)
    }
    private fun removeProgressListener() {
        progressUpdater?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        progressUpdater = null
    }
}
