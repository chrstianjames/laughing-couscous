package com.shortly.app.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement

private fun jsSafePlay(video: HTMLVideoElement): Unit =
    js("(function(){ var p = video.play(); if (p && p.catch) { p.catch(function(){}); } })()")
private fun jsErrorMessage(video: HTMLVideoElement): String = js(
    """(function(){
        var e = video.error; if (!e) return 'Playback error';
        switch (e.code) {
            case 1: return 'Playback aborted';
            case 2: return 'Network error while loading video';
            case 3: return 'Video file is corrupt or unsupported';
            case 4: return 'This browser cannot play the video format';
            default: return 'Playback error';
        }
    })()"""
)

/**
 * HTML <video> element positioned behind the transparent Compose canvas.
 * [VideoPlayerView] keeps its CSS box in sync with the composable's bounds.
 */
actual class VideoPlayer actual constructor(actual val url: String, private val listener: VideoPlayerListener) {

    val element: HTMLVideoElement = document.createElement("video") as HTMLVideoElement
    private var wantPlay = false

    init {
        element.src = url
        element.preload = "auto"
        element.setAttribute("playsinline", "")
        element.setAttribute("webkit-playsinline", "")
        element.crossOrigin = "anonymous"
        with(element.style) {
            position = "fixed"
            left = "0px"; top = "0px"; width = "0px"; height = "0px"
            zIndex = "0"
            display = "none"
            backgroundColor = "black"
            setProperty("object-fit", "cover")
            setProperty("pointer-events", "none")
        }
        element.onloadstart = { listener.onStateChanged(PlaybackState.Loading); null }
        element.onwaiting = { listener.onStateChanged(PlaybackState.Loading); null }
        element.oncanplay = { listener.onStateChanged(PlaybackState.Ready); null }
        element.onloadeddata = { listener.onFirstFrame(); null }
        element.onplaying = { listener.onIsPlayingChanged(true); listener.onStateChanged(PlaybackState.Ready); null }
        element.onpause = { listener.onIsPlayingChanged(false); null }
        element.onended = { listener.onStateChanged(PlaybackState.Ended); null }
        element.onerror = { _, _, _, _, _ -> listener.onError(jsErrorMessage(element)); null }
        document.body?.appendChild(element)
        element.load()
    }

    actual fun play() { wantPlay = true; jsSafePlay(element) }
    actual fun pause() { wantPlay = false; element.pause() }
    actual val isPlayWhenReady: Boolean get() = wantPlay
    actual fun setMuted(muted: Boolean) { element.muted = muted }
    actual fun setLooping(loop: Boolean) { element.loop = loop }
    actual fun seekTo(positionMs: Long) { element.currentTime = positionMs / 1000.0 }
    actual val currentPositionMs: Long get() = (element.currentTime * 1000).toLong()
    actual val durationMs: Long get() {
        val d = element.duration
        return if (d.isFinite() && d > 0) (d * 1000).toLong() else 0L
    }
    actual fun retry() {
        element.load()
        if (wantPlay) jsSafePlay(element)
    }
    actual fun release() {
        element.pause()
        element.removeAttribute("src")
        element.load()
        element.remove()
    }

    internal fun setBounds(left: Double, top: Double, width: Double, height: Double) {
        with(element.style) {
            this.left = "${left}px"; this.top = "${top}px"
            this.width = "${width}px"; this.height = "${height}px"
        }
    }

    internal fun configure(zoom: Boolean, posterUrl: String?, showControls: Boolean) {
        element.style.setProperty("object-fit", if (zoom) "cover" else "contain")
        if (posterUrl != null) element.poster = posterUrl else element.removeAttribute("poster")
        element.controls = showControls
        // Controls need clicks; the feed player is driven by Compose gestures.
        element.style.setProperty("pointer-events", if (showControls) "auto" else "none")
        element.style.display = "block"
    }

    internal fun hide() { element.style.display = "none" }
}

@Composable
actual fun VideoPlayerView(
    player: VideoPlayer,
    modifier: Modifier,
    zoom: Boolean,
    posterUrl: String?,
    showControls: Boolean
) {
    val density = LocalDensity.current.density
    DisposableEffect(player, zoom, posterUrl, showControls) {
        player.configure(zoom, posterUrl, showControls)
        onDispose { player.hide() }
    }
    Box(
        modifier.onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            player.setBounds(r.left / density, r.top / density, r.width / density, r.height / density)
        }
    )
}
