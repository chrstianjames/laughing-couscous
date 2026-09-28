package com.shortly.app.ui.components

import com.shortly.app.platform.PlaybackState
import com.shortly.app.platform.VideoPlayer
import com.shortly.app.platform.VideoPlayerListener
import com.shortly.app.platform.defaultMuted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One shared player for the feed (only the visible card plays). Platform
 * agnostic: the actual engine is the expect/actual [VideoPlayer].
 */
object PlayerPool {

    private var currentPlayer: VideoPlayer? = null
    private var currentUrl: String? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _isMuted = MutableStateFlow(defaultMuted)
    val isMuted: StateFlow<Boolean> = _isMuted
    private val _progress = MutableStateFlow(0L to 0L)
    val progress: StateFlow<Pair<Long, Long>> = _progress
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState

    sealed class PlayerState {
        object Idle : PlayerState()
        object Loading : PlayerState()
        object Ready : PlayerState()
        data class Error(val message: String) : PlayerState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val listener = object : VideoPlayerListener {
        override fun onStateChanged(state: PlaybackState) {
            _playerState.value = when (state) {
                PlaybackState.Idle -> PlayerState.Idle
                PlaybackState.Loading -> PlayerState.Loading
                PlaybackState.Ready, PlaybackState.Ended -> PlayerState.Ready
            }
        }
        override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
        override fun onError(message: String) { _playerState.value = PlayerState.Error(message) }
        override fun onFirstFrame() { if (_playerState.value is PlayerState.Loading) _playerState.value = PlayerState.Ready }
    }

    fun acquire(url: String, playWhenReady: Boolean = true): VideoPlayer {
        currentPlayer?.let { p ->
            if (currentUrl == url) {
                if (playWhenReady) p.play() else p.pause()
                startProgress()
                return p
            }
        }
        release()
        _playerState.value = PlayerState.Loading
        val player = VideoPlayer(url, listener)
        player.setLooping(true)
        player.setMuted(_isMuted.value)
        if (playWhenReady) player.play() else player.pause()
        currentPlayer = player
        currentUrl = url
        startProgress()
        return player
    }

    fun release() {
        stopProgress()
        currentPlayer?.release()
        currentPlayer = null
        currentUrl = null
        _isPlaying.value = false
        _progress.value = 0L to 0L
        _playerState.value = PlayerState.Idle
    }

    fun pause() { currentPlayer?.pause() }
    fun resume() { currentPlayer?.play() }

    fun togglePlay() {
        currentPlayer?.let { if (it.isPlayWhenReady) it.pause() else it.play() }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        currentPlayer?.setMuted(_isMuted.value)
    }

    fun seekTo(ms: Long) { currentPlayer?.seekTo(ms) }

    /** Re-prepare the current item after a playback error. */
    fun retry() {
        currentPlayer?.let {
            _playerState.value = PlayerState.Loading
            it.retry()
            it.play()
        }
    }

    private fun startProgress() {
        stopProgress()
        progressJob = scope.launch {
            while (isActive) {
                currentPlayer?.let { p ->
                    val d = p.durationMs
                    if (d > 0) _progress.value = p.currentPositionMs to d
                }
                delay(200)
            }
        }
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }
}
