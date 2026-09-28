package com.shortly.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Everything the shared Compose Multiplatform code needs from the host
 * platform. Android implementations live in `androidMain`, the browser
 * (Kotlin/Wasm) implementations in `wasmJsMain`.
 */

/** Simple persistent string key/value storage (SharedPreferences / localStorage). */
expect class KeyValueStore() {
    fun get(key: String): String?
    fun put(key: String, value: String?)
    fun clear()
}

/** Dispatcher for blocking-ish work. Wasm has no Dispatchers.IO. */
expect val IoDispatcher: CoroutineDispatcher

/** A Ktor client with the platform engine configured. */
expect fun createPlatformHttpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): HttpClient

/** Epoch millis. */
expect fun currentTimeMillis(): Long

/** Format a double with one decimal (no java.lang.String.format on Wasm). */
fun formatOneDecimal(v: Double): String {
    val rounded = kotlin.math.round(v * 10) / 10.0
    val whole = rounded.toLong()
    val frac = kotlin.math.abs(((rounded - whole) * 10).toLong())
    return "$whole.$frac"
}

/** Share plain text (Android share sheet / Web Share API or clipboard). */
expect fun shareText(text: String)

/** True when the platform is the browser build. */
expect val isWeb: Boolean

/**
 * Background color drawn behind video surfaces. On Android the player is a
 * native view inside the Compose hierarchy, so black is fine. On the web the
 * HTML <video> element sits *behind* the (transparent) Compose canvas, so this
 * must be transparent or the video would be painted over.
 */
expect val videoBackdropColor: Color

/**
 * Whether Compose should draw the poster image itself while the video loads.
 * On web the poster is set on the <video> element instead (drawing it in the
 * canvas would hide the element underneath).
 */
expect val composeDrawsVideoPoster: Boolean

/** Autoplay-with-sound is blocked by browsers; start muted there. */
expect val defaultMuted: Boolean

// ---------------------------------------------------------------- files

/**
 * A file picked by the user. [handle] is platform specific
 * (java.io.File on Android, org.w3c.files.Blob on web) and is consumed by
 * the platform upload implementation. [previewUrl] can be given to a
 * [VideoPlayer] or an image loader for local preview.
 */
class PickedFile(
    val name: String,
    val mimeType: String,
    val size: Long,
    val previewUrl: String,
    val handle: Any
)

/**
 * Returns a launcher that opens the platform file picker for the given
 * MIME filter ("video/*", "image/*") and delivers the picked file.
 */
@Composable
expect fun rememberFilePicker(mimeFilter: String, onPicked: (PickedFile?) -> Unit): () -> Unit

class VideoProbe(val durationSec: Float, val thumbnail: PickedFile?)

/** Read duration and grab a poster frame from a local video. Never throws. */
expect suspend fun probeVideo(file: PickedFile): VideoProbe

class UploadResponse(val status: Int, val body: String)

/**
 * Multipart upload without buffering the whole file through Kotlin memory:
 * Android streams the file with Ktor, web hands the Blob to fetch/FormData.
 */
expect suspend fun platformMultipartUpload(
    url: String,
    token: String?,
    fields: Map<String, String>,
    files: List<Pair<String, PickedFile>>
): UploadResponse

// ---------------------------------------------------------------- video

enum class PlaybackState { Idle, Loading, Ready, Ended }

interface VideoPlayerListener {
    fun onStateChanged(state: PlaybackState)
    fun onIsPlayingChanged(playing: Boolean)
    fun onError(message: String)
    fun onFirstFrame()
}

/** A single video player instance (ExoPlayer / HTMLVideoElement). */
expect class VideoPlayer(url: String, listener: VideoPlayerListener) {
    val url: String
    fun play()
    fun pause()
    val isPlayWhenReady: Boolean
    fun setMuted(muted: Boolean)
    fun setLooping(loop: Boolean)
    fun seekTo(positionMs: Long)
    val currentPositionMs: Long
    val durationMs: Long
    /** Re-prepare after an error. */
    fun retry()
    fun release()
}

/**
 * Renders [player]. [zoom] = crop-to-fill (feed), otherwise fit.
 * [posterUrl] is used by platforms whose surface can show its own poster.
 */
@Composable
expect fun VideoPlayerView(
    player: VideoPlayer,
    modifier: Modifier = Modifier,
    zoom: Boolean = true,
    posterUrl: String? = null,
    showControls: Boolean = false
)

/** Style status/navigation bars (Android edge-to-edge). No-op on web. */
@Composable
expect fun ApplySystemBarsStyle(darkTheme: Boolean)
