package com.shortly.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.shortly.app.data.repository.UserRepository
import com.shortly.app.platform.AppGraph
import com.shortly.app.ui.navigation.AppNavHost
import com.shortly.app.ui.theme.ShortlyTheme

/**
 * Root of the shared UI. Android's MainActivity and the web entry point both
 * just call this.
 */
@Composable
fun App() {
    val darkPref by AppGraph.tokenManager.darkModeFlow.collectAsState()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val token = AppGraph.tokenManager.getToken()
        startDestination = if (token != null) {
            try { UserRepository.instance.loadMe() } catch (_: Exception) {}
            // loadMe() clears the token when it is rejected by the server
            if (AppGraph.tokenManager.getToken() != null) "main" else "auth"
        } else "auth"
    }

    val isDark = darkPref ?: isSystemInDarkTheme()
    ShortlyTheme(darkTheme = isDark) {
        startDestination?.let { dest -> AppNavHost(startDestination = dest) }
    }
}
