package com.shortly.app.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Must be called once from the Application before any shared code runs. */
object AndroidPlatform {
    @SuppressLint("StaticFieldLeak")
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

actual class KeyValueStore actual constructor() {
    private val prefs = AndroidPlatform.appContext.getSharedPreferences("shortly_sync", Context.MODE_PRIVATE)
    actual fun get(key: String): String? = prefs.getString(key, null)
    actual fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
    actual fun clear() { prefs.edit().clear().apply() }
}

actual val IoDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        config()
        engine {
            config {
                retryOnConnectionFailure(true)
            }
        }
    }

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    AndroidPlatform.appContext.startActivity(chooser)
}

actual val isWeb: Boolean = false
actual val videoBackdropColor: Color = Color.Black
actual val composeDrawsVideoPoster: Boolean = true
actual val defaultMuted: Boolean = false

@Composable
actual fun ApplySystemBarsStyle(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
