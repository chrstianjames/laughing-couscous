package com.shortly.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shortly.app.platform.AppGraph
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tokenManager = AppGraph.tokenManager
    var darkMode by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        tokenManager.darkModeFlow.collect { darkMode = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Dark Mode") },
                supportingContent = { Text("Choose light or dark theme") },
                trailingContent = {
                    when (darkMode) {
                        true -> TextButton(onClick = {
                            scope.launch { tokenManager.setDarkMode(null) }
                        }) { Text("On") }
                        false -> TextButton(onClick = {
                            scope.launch { tokenManager.setDarkMode(true) }
                        }) { Text("Off") }
                        null -> TextButton(onClick = {
                            scope.launch { tokenManager.setDarkMode(false) }
                        }) { Text("Auto") }
                    }
                },
                leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) }
            )
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("Shortly 1.0.0") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text("Privacy & Safety") },
                supportingContent = { Text("Learn about our community guidelines") },
                leadingContent = { Icon(Icons.Default.Security, contentDescription = null) }
            )
        }
    }
}
