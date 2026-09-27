package com.shortly.app.ui.screens.upload

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.shortly.app.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Everything we know about the picked video once it has been copied locally. */
private data class PreparedVideo(
    val file: File,
    val mimeType: String,
    val durationSec: Float,
    val thumbnail: File?,
    val poster: Bitmap?
)

@OptIn(UnstableApi::class)
@Composable
fun UploadScreen(onUploaded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var prepared by remember { mutableStateOf<PreparedVideo?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewReady by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Local preview player. Keyed on the *copied file* (not the content://
    // URI): some pickers hand out URIs that are only readable once, which left
    // the preview black. The copied file in cacheDir is always readable.
    DisposableEffect(prepared?.file) {
        val file = prepared?.file
        previewReady = false
        previewError = null
        val p = if (file != null) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) previewReady = true
                    }
                    override fun onRenderedFirstFrame() {
                        previewReady = true
                    }
                    override fun onPlayerError(e: PlaybackException) {
                        previewError = "This video cannot be previewed on this device (${e.errorCodeName}). You can still post it."
                    }
                })
                prepare()
                // Autoplay muted so the user actually sees frames instead of a
                // black surface waiting for a tap.
                playWhenReady = true
            }
        } else null
        player = p
        onDispose {
            p?.release()
            if (player === p) player = null
        }
    }

    // Pause preview while uploading
    LaunchedEffect(uploading) { player?.playWhenReady = !uploading }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        error = null
        prepared = null
        videoUri = uri
        preparing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { prepareVideo(context, uri) } }
            preparing = false
            result.onSuccess { prepared = it }
            result.onFailure {
                videoUri = null
                error = it.message ?: "Could not read the selected video"
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickVideo.launch("video/*")
        else error = "Storage permission needed"
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
        } else if (videoUri == null) {
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
                    onClick = {
                        error = null
                        // The system photo picker (GetContent) does not need
                        // storage permission on Android 13+, but older devices
                        // still need READ_EXTERNAL_STORAGE for file:// results.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pickVideo.launch("video/*")
                        } else {
                            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
                            if (context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                pickVideo.launch("video/*")
                            } else {
                                permLauncher.launch(perm)
                            }
                        }
                    },
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
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val poster = prepared?.poster
                    // Poster frame shown until the player has rendered a frame
                    // (and as the permanent fallback if the codec is unsupported).
                    if (poster != null && (!previewReady || previewError != null)) {
                        Image(
                            bitmap = poster.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    val p = player
                    if (p != null && previewError == null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    controllerShowTimeoutMs = 1500
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    setKeepContentOnPlayerReset(true)
                                    this.player = p
                                }
                            },
                            update = { view -> if (view.player !== p) view.player = p },
                            modifier = Modifier.fillMaxSize()
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
                    prepared?.let { pv ->
                        val mb = pv.file.length() / (1024f * 1024f)
                        Text(
                            String.format("%.1f MB · %s · %s", mb, pv.mimeType, formatSeconds(pv.durationSec)),
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
                                prepared?.file?.delete()
                                prepared?.thumbnail?.delete()
                                videoUri = null
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
                                        mimeType = pv.mimeType,
                                        thumbnail = pv.thumbnail
                                    )
                                    uploading = false
                                    res.onSuccess {
                                        pv.file.delete()
                                        pv.thumbnail?.delete()
                                        onUploaded()
                                    }
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
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Copies the picked content URI to the app cache with the *right* extension,
 * reads its duration and generates a poster frame. Runs on Dispatchers.IO.
 */
private fun prepareVideo(context: Context, uri: Uri): PreparedVideo {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
        )
        ?: "video/mp4"

    // Prefer the real display name's extension, fall back to the MIME map.
    var displayName: String? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) displayName = c.getString(0)
        }
    }
    val extFromName = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    val ext = when {
        extFromName in setOf("mp4", "mov", "webm", "m4v", "3gp", "mkv") -> extFromName
        else -> when (mime) {
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            "video/x-m4v" -> "m4v"
            "video/3gpp" -> "3gp"
            "video/x-matroska" -> "mkv"
            else -> "mp4"
        }
    }

    val stamp = System.currentTimeMillis()
    val file = File(context.cacheDir, "upload_$stamp.$ext")
    val input = resolver.openInputStream(uri)
        ?: throw IllegalStateException("Could not open the selected video")
    input.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
    if (file.length() == 0L) {
        file.delete()
        throw IllegalStateException("The selected video is empty or unreadable")
    }

    var durationSec = 0f
    var poster: Bitmap? = null
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        durationSec = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000f
        // Grab a frame ~0.5s in (first frame is often black/fade-in)
        val atUs = if (durationSec > 1f) 500_000L else 0L
        poster = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (_: Exception) {
        // Non-fatal: preview/poster are optional
    } finally {
        runCatching { retriever.release() }
    }

    var thumbFile: File? = null
    poster?.let { bmp ->
        // Downscale for upload (max 720px on the long side)
        val scale = 720f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        val f = File(context.cacheDir, "upload_${stamp}_thumb.jpg")
        runCatching {
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            thumbFile = f
        }
        if (scaled !== bmp) scaled.recycle()
    }

    return PreparedVideo(file = file, mimeType = mime, durationSec = durationSec, thumbnail = thumbFile, poster = poster)
}
