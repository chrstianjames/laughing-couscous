package com.shortly.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// ---- small JS helpers (Kotlin/Wasm js() bodies must be single expressions) ----
private fun jsDateNow(): Double = js("Date.now()")
private fun jsShareText(text: String): Unit = js(
    """(function(){
        if (navigator.share) { navigator.share({ text: text }).catch(function(){}); }
        else if (navigator.clipboard) { navigator.clipboard.writeText(text).then(function(){ alert('Copied to clipboard'); }); }
        else { window.prompt('Copy this text', text); }
    })()"""
)

actual class KeyValueStore actual constructor() {
    private val prefix = "shortly."
    actual fun get(key: String): String? = localStorage.getItem(prefix + key)
    actual fun put(key: String, value: String?) {
        if (value == null) localStorage.removeItem(prefix + key) else localStorage.setItem(prefix + key, value)
    }
    actual fun clear() {
        val keys = (0 until localStorage.length).mapNotNull { localStorage.key(it) }.filter { it.startsWith(prefix) }
        keys.forEach { localStorage.removeItem(it) }
    }
}

/** Kotlin/Wasm has no IO dispatcher; browser requests are async anyway. */
actual val IoDispatcher: CoroutineDispatcher = Dispatchers.Default

actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Js) { config() }

actual fun currentTimeMillis(): Long = jsDateNow().toLong()

actual fun shareText(text: String) = jsShareText(text)

actual val isWeb: Boolean = true

// The <video> element sits behind the transparent Compose canvas.
actual val videoBackdropColor: Color = Color.Transparent
actual val composeDrawsVideoPoster: Boolean = false
// Browsers block autoplay with sound.
actual val defaultMuted: Boolean = true

@Composable
actual fun ApplySystemBarsStyle(darkTheme: Boolean) { /* no system bars in the browser */ }
