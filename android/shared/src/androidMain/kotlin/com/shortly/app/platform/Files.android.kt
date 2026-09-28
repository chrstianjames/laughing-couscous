package com.shortly.app.platform

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberFilePicker(mimeFilter: String, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) { onPicked(null); return@rememberLauncherForActivityResult }
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { copyToCache(context, uri, mimeFilter) }.getOrNull() }
            onPicked(file)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) picker.launch(mimeFilter) else onPicked(null)
    }

    return {
        // The system picker (GetContent) needs no permission on Android 13+;
        // older devices still need READ_EXTERNAL_STORAGE for file:// results.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            picker.launch(mimeFilter)
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                picker.launch(mimeFilter)
            } else {
                permission.launch(perm)
            }
        }
    }
}

private val videoExts = setOf("mp4", "mov", "webm", "m4v", "3gp", "mkv")
private val imageExts = setOf("jpg", "jpeg", "png", "webp")

/** Copies a content:// URI into the cache dir with a sensible extension. */
private fun copyToCache(context: Context, uri: Uri, mimeFilter: String): PickedFile {
    val resolver = context.contentResolver
    val isVideo = mimeFilter.startsWith("video")
    val mime = resolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase())
        ?: if (isVideo) "video/mp4" else "image/jpeg"

    var displayName: String? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) displayName = c.getString(0)
        }
    }
    val extFromName = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    val ext = when {
        isVideo && extFromName in videoExts -> extFromName
        !isVideo && extFromName in imageExts -> extFromName
        else -> when (mime) {
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            "video/x-m4v" -> "m4v"
            "video/3gpp" -> "3gp"
            "video/x-matroska" -> "mkv"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> if (isVideo) "mp4" else "jpg"
        }
    }
    val prefix = if (isVideo) "upload" else "avatar"
    val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.$ext")
    val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Could not open the selected file")
    input.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
    if (file.length() == 0L) {
        file.delete()
        throw IllegalStateException("The selected file is empty or unreadable")
    }
    return PickedFile(
        name = file.name,
        mimeType = mime,
        size = file.length(),
        previewUrl = Uri.fromFile(file).toString(),
        handle = file
    )
}

actual suspend fun probeVideo(file: PickedFile): VideoProbe = withContext(Dispatchers.IO) {
    val src = file.handle as? File ?: return@withContext VideoProbe(0f, null)
    var durationSec = 0f
    var poster: Bitmap? = null
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(src.absolutePath)
        durationSec = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000f
        // ~0.5s in: the first frame is often black / a fade-in
        val atUs = if (durationSec > 1f) 500_000L else 0L
        poster = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (_: Exception) {
    } finally {
        runCatching { retriever.release() }
    }

    var thumb: PickedFile? = null
    poster?.let { bmp ->
        val scale = 720f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
        ) else bmp
        val f = File(src.parentFile, src.nameWithoutExtension + "_thumb.jpg")
        runCatching {
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            thumb = PickedFile(f.name, "image/jpeg", f.length(), Uri.fromFile(f).toString(), f)
        }
        if (scaled !== bmp) scaled.recycle()
    }
    VideoProbe(durationSec, thumb)
}

actual suspend fun platformMultipartUpload(
    url: String,
    token: String?,
    fields: Map<String, String>,
    files: List<Pair<String, PickedFile>>
): UploadResponse = withContext(Dispatchers.IO) {
    val client = createPlatformHttpClient {
        expectSuccess = false
        install(io.ktor.client.plugins.HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 15 * 60_000
            socketTimeoutMillis = 15 * 60_000
        }
    }
    try {
        val response = client.post(url) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.Accept, "application/json")
            setBody(MultiPartFormDataContent(formData {
                fields.forEach { (k, v) -> append(k, v) }
                files.forEach { (key, pf) ->
                    val f = pf.handle as File
                    // Stream from disk instead of loading 100MB into memory
                    append(key, ChannelProvider(f.length()) { f.readChannel() }, Headers.build {
                        append(HttpHeaders.ContentType, pf.mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${pf.name}\"")
                    })
                }
            }))
        }
        UploadResponse(response.status.value, response.bodyAsText())
    } finally {
        client.close()
    }
}
