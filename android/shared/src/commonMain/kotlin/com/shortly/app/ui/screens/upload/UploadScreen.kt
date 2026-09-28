package com.shortly.app.ui.screens.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shortly.app.data.repository.VideoRepository
import com.shortly.app.platform.PickedFile
import com.shortly.app.platform.PlaybackState
import com.shortly.app.platform.VideoPlayer
import com.shortly.app.platform.VideoPlayerListener
import com.shortly.app.platform.VideoPlayerView
import com.shortly.app.platform.formatOneDecimal
import com.shortly.app.platform.probeVideo
import com.shortly.app.platform.rememberFilePicker
import com.shortly.app.platform.videoBackdropColor
import kotlinx.coroutines.launch

/** Everything we know about the picked video once it has been prepared. */
private data class PreparedVideo(
    val file: PickedFile,
    val durationSec: Float,
    val thumbnail: PickedFile?
)

@Composable
fun UploadScreen(onUploaded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var prepared by remember { mutableStateOf<PreparedVideo?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewReady by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<VideoPlayer?>(null) }

    // Local preview player, keyed on the picked file.
    DisposableEffect(picked) {
        val file = picked
        previewReady = false
        previewError = null
        val p = if (file != null) {
            VideoPlayer(file.previewUrl, object : VideoPlayerListener {
                override fun onStateChanged(state: PlaybackState) {
                    if (state == PlaybackState.Ready) previewReady = true
                }
                override fun onIsPlayingChanged(playing: Boolean) {}
                override fun onError(message: String) {
                    previewError = "This video cannot be previewed here ($message). You can still post it."
                }
                override fun onFirstFrame() { previewReady = true }
            }).apply {
                setLooping(true)
                setMuted(true)
                // Autoplay muted so the user sees frames instead of a black surface.
                play()
            }
        } else null
        player = p
        onDispose {
            p?.release()
            if (player === p) player = null
        }
    }

    LaunchedEffect(uploading) { if (uploading) player?.pause() else player?.play() }

    val pickVideo = rememberFilePicker("video/*") { file ->
        file ?: return@rememberFilePicker
        error = null
        prepared = null
        picked = file
        preparing = true
        scope.launch {
            val probe = runCatching { probeVideo(file) }.getOrNull()
            preparing = false
            prepared = PreparedVideo(file, probe?.durationSec ?: 0f, probe?.thumbnail)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (uploading) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(strokeWidth = 4.dp, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(16.dp))
                Text("Uploading...", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Keep the app open until this finishes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (picked == null) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Upload a Video", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Share a moment with the world", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { error = null; pickVideo() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Video", style = MaterialTheme.typography.titleMedium)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            // Edit post form
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Video preview
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(videoBackdropColor),
                    contentAlignment = Alignment.Center
                ) {
                    val poster = prepared?.thumbnail
                    if (poster != null && (!previewReady || previewError != null)) {
                        AsyncImage(
                            model = poster.previewUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    val p = player
                    if (p != null && previewError == null) {
                        VideoPlayerView(
                            player = p,
                            modifier = Modifier.fillMaxSize(),
                            zoom = false,
                            posterUrl = poster?.previewUrl,
                            showControls = true
                        )
                    }
                    if (preparing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("Preparing video...", color = Color.White)
                        }
                    }
                    previewError?.let {
                        Text(
                            it,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(8.dp)
                        )
                    }
                }

                Column(Modifier.padding(16.dp)) {
                    picked?.let { pv ->
                        val mb = pv.size / (1024.0 * 1024.0)
                        val dur = prepared?.durationSec ?: 0f
                        Text(
                            formatOneDecimal(mb) + " MB · " + pv.mimeType + " · " + formatSeconds(dur),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Caption") },
                        placeholder = { Text("Describe your video... Use #hashtags") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                picked = null
                                prepared = null
                                error = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Change Video") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            enabled = prepared != null && !preparing,
                            onClick = {
                                val pv = prepared ?: return@Button
                                error = null
                                uploading = true
                                scope.launch {
                                    val res = VideoRepository.instance.uploadVideo(
                                        file = pv.file,
                                        caption = caption,
                                        duration = pv.durationSec,
                                        thumbnail = pv.thumbnail
                                    )
                                    uploading = false
                                    res.onSuccess { onUploaded() }
                                    res.onFailure { error = it.message ?: "Upload failed" }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (preparing) "Preparing..." else "Post", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun formatSeconds(sec: Float): String {
    val total = sec.toInt()
    return "${total / 60}:" + (total % 60).toString().padStart(2, '0')
}
