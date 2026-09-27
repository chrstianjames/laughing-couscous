package com.shortly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.shortly.app.data.repository.UserRepository
import com.shortly.app.ui.navigation.AppNavHost
import com.shortly.app.ui.theme.ShortlyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val scope = rememberCoroutineScope()
            var darkMode by remember { mutableStateOf<Boolean?>(null) }
            var startDestination by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                ShortlyApp.instance.tokenManager.darkModeFlow.collect { darkMode = it }
            }

            LaunchedEffect(Unit) {
                val token = ShortlyApp.instance.tokenManager.tokenFlow.first()
                startDestination = if (token != null) {
                    try { UserRepository.instance.loadMe() } catch (_: Exception) {}
                    "main"
                } else "auth"
            }

            val isDark = darkMode ?: (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            ShortlyTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    startDestination?.let { dest ->
                        AppNavHost(startDestination = dest)
                    }
                }
            }
        }
    }
}
