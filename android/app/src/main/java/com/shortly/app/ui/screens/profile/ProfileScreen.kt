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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shortly.app.data.model.User
import com.shortly.app.data.model.Video
import com.shortly.app.data.repository.SocialRepository
import com.shortly.app.data.repository.UserRepository
import com.shortly.app.data.repository.VideoRepository
import com.shortly.app.ui.components.*
import com.shortly.app.ui.util.Utils
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    isCurrentUser: Boolean,
    username: String = "",
    onEditProfile: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onFollowers: (String) -> Unit,
    onFollowing: (String) -> Unit,
    onSaved: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val me by UserRepository.instance.currentUser.collectAsState()
    var user by remember { mutableStateOf<User?>(if (isCurrentUser) me else null) }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val targetUsername = if (isCurrentUser) me?.username ?: "" else username

    fun loadUser() {
        if (targetUsername.isBlank()) return
        scope.launch {
            loading = true
            SocialRepository.instance.getUser(targetUsername).onSuccess { user = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    fun loadVideos(reset: Boolean = false) {
        if (targetUsername.isBlank()) return
        if (reset) { page = 1; hasMore = true; videos = emptyList() }
        scope.launch {
            VideoRepository.instance.getUserVideos(targetUsername, page).onSuccess { res ->
                videos = if (reset) res.items else videos + res.items
                hasMore = res.hasMore
                if (res.hasMore) page++
            }
        }
    }

    LaunchedEffect(isCurrentUser, me, username) {
        if (isCurrentUser) {
            user = me
            if (me == null) UserRepository.instance.loadMe()
        }
        loadUser()
        loadVideos(reset = true)
    }

    val listState = rememberLazyGridState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { idx ->
                if (idx != null && idx >= videos.size - 6 && hasMore && !loading) loadVideos()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@${user?.username ?: ""}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (isCurrentUser) {
                        if (onSaved != null) IconButton(onClick = onSaved) { Icon(Icons.Default.BookmarkBorder, contentDescription = "Saved") }
                        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                        if (onLogout != null) IconButton(onClick = {
                            scope.launch { UserRepository.instance.logout(); onLogout() }
                        }) { Icon(Icons.Default.Logout, contentDescription = "Logout") }
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading && user == null -> LoadingState(modifier = Modifier.padding(padding))
            error != null && user == null -> ErrorState(error ?: "Error", onRetry = { loadUser(); loadVideos(true) }, modifier = Modifier.padding(padding))
            user != null -> {
                val u = user!!
                LazyVerticalGrid(
                    state = listState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        ProfileHeader(
                            user = u,
                            isCurrentUser = isCurrentUser,
                            onEditProfile = onEditProfile ?: {},
                            onFollowers = { onFollowers(u.username) },
                            onFollowing = { onFollowing(u.username) },
                            onFollow = {
                                scope.launch {
                                    val nowFollowing = if (u.isFollowing == true) {
                                        SocialRepository.instance.unfollow(u.username).getOrNull() ?: false
                                    } else {
                                        SocialRepository.instance.follow(u.username).getOrNull() ?: true
                                    }
                                    user = u.copy(isFollowing = nowFollowing)
                                    loadUser()
                                }
                            },
                            onMessage = { /* not implemented */ },
                            onBlock = {
                                scope.launch {
                                    if (u.isBlocked == true) SocialRepository.instance.unblock(u.username)
                                    else SocialRepository.instance.block(u.username)
                                    loadUser()
                                }
                            }
                        )
                    }
                    items(videos, key = { it.id }) { v ->
                        Box(
                            modifier = Modifier.aspectRatio(9f/16f)
                                .clip(RoundedCornerShape(0.dp))
                                .background(Color.Black)
                        ) {
                            if (v.thumbnailUrl != null) {
                                AsyncImage(model = v.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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

@Composable
fun ProfileHeader(
    user: User,
    isCurrentUser: Boolean,
    onEditProfile: () -> Unit,
    onFollowers: () -> Unit,
    onFollowing: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onBlock: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Avatar(user, size = 96.dp, showStroke = true)
        Spacer(Modifier.height(12.dp))
        Text(user.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (user.bio.isNotBlank()) {
            Text(user.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFollowing() }) {
                Text(Utils.formatCount(user.followingCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Following", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFollowers() }) {
                Text(Utils.formatCount(user.followersCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Followers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (isCurrentUser) {
            OutlinedButton(
                onClick = onEditProfile,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) { Text("Edit Profile") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onFollow,
                    shape = RoundedCornerShape(8.dp),
                    colors = if (user.isFollowing == true) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) else ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Text(if (user.isFollowing == true) "Following" else "Follow", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onBlock,
                    shape = RoundedCornerShape(8.dp),
                    colors = if (user.isBlocked == true) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(if (user.isBlocked == true) Icons.Default.Block else Icons.Default.Block, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Videos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
