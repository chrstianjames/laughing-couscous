package com.shortly.app.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shortly.app.data.model.User
import com.shortly.app.data.repository.SocialRepository
import com.shortly.app.ui.components.*
import com.shortly.app.ui.util.Utils
import kotlinx.coroutines.launch

@Composable
fun FollowListScreen(
    username: String,
    type: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(reset: Boolean = false) {
        if (reset) { page = 1; hasMore = true; users = emptyList() }
        scope.launch {
            loading = true
            val result = if (type == "following") SocialRepository.instance.getFollowing(username, page)
                         else SocialRepository.instance.getFollowers(username, page)
            result.onSuccess {
                users = if (reset) it.items else users + it.items
                hasMore = it.hasMore
                if (hasMore) page++
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(username, type) { load(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("@$username", fontWeight = FontWeight.Bold)
                        Text(if (type == "following") "Following" else "Followers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && users.isEmpty() -> LoadingState()
                error != null && users.isEmpty() -> ErrorState(error ?: "Error", onRetry = { load(true) })
                users.isEmpty() -> EmptyState(if (type == "following") "Not following anyone yet" else "No followers yet")
                else -> LazyColumn {
                    items(users, key = { it.id }) { u ->
                        ListItem(
                            modifier = Modifier.clickable { onUserClick(u.username) },
                            leadingContent = { Avatar(u, size = 48.dp) },
                            headlineContent = { Text(u.displayName, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("@${u.username} · ${Utils.formatCount(u.followersCount)} followers") },
                            trailingContent = {
                                FollowButton(user = u, onFollowChange = { following ->
                                    users = users.toMutableList().apply {
                                        val i = indexOfFirst { it.id == u.id }
                                        if (i >= 0) set(i, get(i).copy(isFollowing = following))
                                    }
                                })
                            }
                        )
                        Divider()
                    }
                    item {
                        if (hasMore && !loading) LaunchedEffect(Unit) { load() }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun FollowButton(user: User, onFollowChange: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val isMe = user.id == com.shortly.app.data.repository.UserRepository.instance.currentUser.value?.id
    if (isMe) return
    var isFollowing by remember(user.isFollowing) { mutableStateOf(user.isFollowing == true) }
    var loading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            loading = true
            scope.launch {
                val r = if (isFollowing) SocialRepository.instance.unfollow(user.username)
                        else SocialRepository.instance.follow(user.username)
                r.onSuccess { isFollowing = it; onFollowChange(it) }
                loading = false
            }
        },
        enabled = !loading,
        colors = if (isFollowing) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) else ButtonDefaults.buttonColors(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Text(if (isFollowing) "Following" else "Follow", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}
