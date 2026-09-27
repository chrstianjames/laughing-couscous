package com.shortly.app.ui.screens.upload

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.shortly.app.data.repository.VideoRepository
import com.shortly.app.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
@Composable
fun UploadScreen(onUploaded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    var caption by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(0f) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(videoUri) {
        player?.release()
        player = null
        if (videoUri != null) {
            val p = ExoPlayer.Builder(context).build()
            p.setMediaItem(MediaItem.fromUri(videoUri!!))
            p.prepare()
            p.playWhenReady = false
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_READY) {
                        duration = p.duration / 1000f
                    }
                }
            })
            player = p
        }
        onDispose { player?.release() }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            videoUri = it
            scope.launch {
                // Copy to temp file
                withContext(Dispatchers.IO) {
                    val tmp = File(context.cacheDir, "upload_tmp_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    videoFile = tmp
                }
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
                CircularProgressIndicator(progress = { progress }, strokeWidth = 4.dp, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(16.dp))
                Text("Uploading... ${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
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
                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
                        if (context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            pickVideo.launch("video/*")
                        } else {
                            permLauncher.launch(perm)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Video", style = MaterialTheme.typography.titleMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        } else {
            // Edit post form
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Video preview
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f/16f).background(Color.Black)
                ) {
                    val p = player
                    if (p != null) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    player = p
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(Modifier.padding(16.dp)) {
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
                            onClick = { videoUri = null; videoFile = null },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Change Video") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val file = videoFile ?: return@Button
                                uploading = true
                                scope.launch {
                                    val res = VideoRepository.instance.uploadVideo(file, caption, duration)
                                    uploading = false
                                    res.onSuccess { onUploaded() }
                                    res.onFailure { error = it.message }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Post", style = MaterialTheme.typography.titleMedium)
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
