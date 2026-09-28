package com.shortly.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.files.Blob
import org.w3c.files.File
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume

// ---- JS helpers ----
private fun jsCreateObjectUrl(blob: Blob): String = js("URL.createObjectURL(blob)")
private fun jsRevokeObjectUrl(url: String): Unit = js("URL.revokeObjectURL(url)")
private fun jsFirstFile(input: HTMLInputElement): File? = js("(input.files && input.files.length > 0) ? input.files[0] : null")
private fun jsCaptureFrame(video: HTMLVideoElement, maxSide: Int, onDone: (Blob?) -> Unit): Unit = js(
    """(function(){
        try {
            var w = video.videoWidth || 0, h = video.videoHeight || 0;
            if (!w || !h) { onDone(null); return; }
            var scale = Math.min(1, maxSide / Math.max(w, h));
            var c = document.createElement('canvas');
            c.width = Math.max(1, Math.round(w * scale)); c.height = Math.max(1, Math.round(h * scale));
            c.getContext('2d').drawImage(video, 0, 0, c.width, c.height);
            c.toBlob(function(b){ onDone(b); }, 'image/jpeg', 0.82);
        } catch (e) { onDone(null); }
    })()"""
)

@Composable
actual fun rememberFilePicker(mimeFilter: String, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val input = remember(mimeFilter) {
        (document.createElement("input") as HTMLInputElement).apply {
            type = "file"
            accept = mimeFilter
            style.display = "none"
            onchange = {
                val f = jsFirstFile(this)
                value = ""
                callback.value(f?.let { toPicked(it, mimeFilter) })
                null
            }
        }
    }
    DisposableEffect(input) {
        document.body?.appendChild(input)
        onDispose { input.remove() }
    }
    return { input.click() }
}

private fun toPicked(file: File, mimeFilter: String): PickedFile {
    val isVideo = mimeFilter.startsWith("video")
    val mime = file.type.ifBlank { if (isVideo) "video/mp4" else "image/jpeg" }
    return PickedFile(
        name = file.name.ifBlank { if (isVideo) "video.mp4" else "image.jpg" },
        mimeType = mime,
        size = file.size.toDouble().toLong(),
        previewUrl = jsCreateObjectUrl(file),
        handle = file
    )
}

actual suspend fun probeVideo(file: PickedFile): VideoProbe {
    val video = document.createElement("video") as HTMLVideoElement
    video.preload = "metadata"
    video.muted = true
    video.setAttribute("playsinline", "")
    video.src = file.previewUrl

    val duration = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine<Double> { cont ->
            video.onloadedmetadata = { if (cont.isActive) cont.resume(video.duration); null }
            video.onerror = { _, _, _, _, _ -> if (cont.isActive) cont.resume(0.0); null }
        }
    } ?: 0.0
    val durationSec = if (duration.isFinite()) duration.toFloat() else 0f

    // Seek ~0.5s in and grab a frame for the poster
    val blob: Blob? = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine { cont ->
            video.onseeked = {
                jsCaptureFrame(video, 720) { b -> if (cont.isActive) cont.resume(b) }
                null
            }
            video.onerror = { _, _, _, _, _ -> if (cont.isActive) cont.resume(null); null }
            video.currentTime = if (durationSec > 1f) 0.5 else 0.0
        }
    }
    video.removeAttribute("src")
    video.load()

    val thumb = blob?.let {
        PickedFile("thumbnail.jpg", "image/jpeg", it.size.toDouble().toLong(), jsCreateObjectUrl(it), it)
    }
    return VideoProbe(durationSec, thumb)
}

actual suspend fun platformMultipartUpload(
    url: String,
    token: String?,
    fields: Map<String, String>,
    files: List<Pair<String, PickedFile>>
): UploadResponse = suspendCancellableCoroutine { cont ->
    val form = FormData()
    fields.forEach { (k, v) -> form.append(k, v) }
    files.forEach { (k, pf) -> form.append(k, pf.handle as Blob, pf.name) }

    val xhr = XMLHttpRequest()
    xhr.open("POST", url)
    token?.let { xhr.setRequestHeader("Authorization", "Bearer $it") }
    xhr.setRequestHeader("Accept", "application/json")
    xhr.onload = { if (cont.isActive) cont.resume(UploadResponse(xhr.status.toInt(), xhr.responseText)); null }
    xhr.onerror = { if (cont.isActive) cont.resume(UploadResponse(0, "{\"error\":true,\"message\":\"Network error during upload\"}")); null }
    xhr.onabort = { if (cont.isActive) cont.resume(UploadResponse(0, "{\"error\":true,\"message\":\"Upload cancelled\"}")); null }
    cont.invokeOnCancellation { runCatching { xhr.abort() } }
    xhr.send(form)
}
