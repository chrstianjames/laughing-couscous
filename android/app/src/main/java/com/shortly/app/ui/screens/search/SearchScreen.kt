package com.shortly.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shortly.app.data.model.Hashtag
import com.shortly.app.data.model.User
import com.shortly.app.data.model.Video
import com.shortly.app.data.repository.SocialRepository
import com.shortly.app.ui.components.Avatar
import com.shortly.app.ui.components.EmptyState
import com.shortly.app.ui.components.ErrorState
import com.shortly.app.ui.components.LoadingState
import com.shortly.app.ui.util.Utils
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onProfileClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0=all, 1=users, 2=videos, 3=tags
    var results by remember { mutableStateOf<SearchResultsState?>(null) }
    var loading by remember { mutableStateOf(false) }
    var trending by remember { mutableStateOf<List<Hashtag>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        SocialRepository.instance.trendingHashtags().onSuccess { trending = it }
    }

    LaunchedEffect(query, activeTab) {
        if (query.length < 1) { results = null; return@LaunchedEffect }
        loading = true
        val type = when (activeTab) {
            1 -> "users"
            2 -> "videos"
            3 -> "hashtags"
            else -> null
        }
        SocialRepository.instance.search(query, type).onSuccess { results = SearchResultsState(it.users, it.videos, it.hashtags) }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search users, videos, hashtags") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
        // Tabs
        if (query.isNotBlank()) {
            TabRow(selectedTabIndex = activeTab) {
                listOf("All", "Users", "Videos", "Tags").forEachIndexed { i, t ->
                    Tab(selected = activeTab == i, onClick = { activeTab = i }, text = { Text(t) })
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                query.isBlank() && trending.isNotEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text("Trending Hashtags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        items(trending) { tag ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onHashtagClick(tag.tag) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("#${tag.tag}", fontWeight = FontWeight.Bold)
                                        Text("${Utils.formatCount(tag.videosCount)} videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                loading -> LoadingState()
                error != null -> ErrorState(error ?: "Search failed")
                query.isBlank() -> EmptyState("Search", "Find users, videos, and hashtags")
                results != null -> {
                    val r = results!!
                    if (r.users.isEmpty() && r.videos.isEmpty() && r.hashtags.isEmpty()) {
                        EmptyState("No results", "Try a different search")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            if (activeTab == 0 || activeTab == 1) {
                                item { SectionHeader("Users") }
                                items(r.users.take(if (activeTab == 0) 3 else Int.MAX_VALUE)) { u ->
                                    UserResult(u) { onProfileClick(u.username) }
                                }
                            }
                            if (activeTab == 0 || activeTab == 3) {
                                item { SectionHeader("Hashtags") }
                                items(r.hashtags.take(if (activeTab == 0) 5 else Int.MAX_VALUE)) { tag ->
                                    HashtagResult(tag) { onHashtagClick(tag.tag) }
                                }
                            }
                            if (activeTab == 0 || activeTab == 2) {
                                item { SectionHeader("Videos") }
                                item {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        modifier = Modifier.heightIn(min = 200.dp, max = 600.dp).fillMaxWidth(),
                                        contentPadding = PaddingValues(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        items(r.videos.take(if (activeTab == 0) 9 else Int.MAX_VALUE)) { v ->
                                            VideoThumbnail(v) { onVideoClick(v) }
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(64.dp)) }
                        }
                    }
                }
            }
        }
    }
}

data class SearchResultsState(
    val users: List<User>,
    val videos: List<Video>,
    val hashtags: List<Hashtag>
)

@Composable fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
fun UserResult(user: User, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(user.displayName, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("@${user.username}") },
        leadingContent = { Avatar(user, size = 48.dp) }
    )
}

@Composable
fun HashtagResult(tag: Hashtag, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text("#${tag.tag}", fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${Utils.formatCount(tag.videosCount)} videos") },
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("#", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
fun VideoThumbnail(video: Video, onClick: () -> Unit) {
    Box(
        modifier = Modifier.aspectRatio(9f/16f).clip(RoundedCornerShape(0.dp)).clickable(onClick = onClick).background(Color.Black)
    ) {
        if (video.thumbnailUrl != null) {
            AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        // Play count overlay
        Surface(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp), color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
            Text(Utils.formatCount(video.views), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}
