package com.shortly.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shortly.app.data.model.Video
import com.shortly.app.data.repository.VideoRepository
import com.shortly.app.ui.components.EmptyState
import com.shortly.app.ui.components.ErrorState
import com.shortly.app.ui.components.LoadingState
import com.shortly.app.ui.util.Utils
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class UserVideoSource { SAVED, LIKED }

@Composable
fun UserVideosScreen(
    title: String,
    source: UserVideoSource,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(reset: Boolean = false) {
        if (reset) { page = 1; hasMore = true; videos = emptyList() }
        scope.launch {
            loading = true
            val r = when (source) {
                UserVideoSource.SAVED -> VideoRepository.instance.getSaved(page)
                UserVideoSource.LIKED -> VideoRepository.instance.getSaved(page) // Liked not implemented separately, map to saved
            }
            r.onSuccess {
                videos = if (reset) it.items else videos + it.items
                hasMore = it.hasMore
                if (hasMore) page++
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(source) { load(true) }

    val listState = rememberLazyGridState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collectLatest { idx ->
                if (idx != null && idx >= videos.size - 6 && hasMore && !loading) load()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && videos.isEmpty() -> LoadingState()
                error != null && videos.isEmpty() -> ErrorState(error ?: "Error", onRetry = { load(true) })
                videos.isEmpty() -> EmptyState(title, "No videos here")
                else -> LazyVerticalGrid(
                    state = listState,
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(videos, key = { it.id }) { v ->
                        Box(
                            modifier = Modifier.aspectRatio(9f/16f).clip(RoundedCornerShape(0.dp)).background(Color.Black).clickable { onVideoClick(v) }
                        ) {
                            if (v.thumbnailUrl != null) {
                                AsyncImage(v.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                            Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(Utils.formatCount(v.views), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
