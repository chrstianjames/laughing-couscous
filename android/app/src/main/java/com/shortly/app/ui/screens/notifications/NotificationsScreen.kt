package com.shortly.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.shortly.app.data.model.NotificationItem
import com.shortly.app.data.model.Video
import com.shortly.app.data.repository.NotificationRepository
import com.shortly.app.ui.components.Avatar
import com.shortly.app.ui.components.EmptyState
import com.shortly.app.ui.components.ErrorState
import com.shortly.app.ui.components.LoadingState
import com.shortly.app.ui.util.Utils
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    onProfileClick: (String) -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            NotificationRepository.instance.getNotifications(1).onSuccess {
                items = it.items
                NotificationRepository.instance.markRead()
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("Activity", fontWeight = FontWeight.Bold) })
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingState()
                error != null -> ErrorState(error ?: "Error", onRetry = { load() })
                items.isEmpty() -> EmptyState("No activity yet", "Likes, comments, and follows will appear here")
                else -> LazyColumn {
                    items(items, key = { it.id }) { n ->
                        NotificationRow(n, onProfileClick = { n.fromUser?.username?.let(onProfileClick) }, onVideoClick = { n.video?.let(onVideoClick) })
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationRow(
    n: NotificationItem,
    onProfileClick: () -> Unit,
    onVideoClick: () -> Unit
) {
    val (icon, color, text) = when (n.type) {
        "like" -> Triple(Icons.Default.Favorite, Color(0xFFFF2E7D), "liked your video")
        "comment" -> Triple(Icons.Default.ChatBubble, Color(0xFF00E5FF), "commented: ")
        "reply" -> Triple(Icons.Default.Reply, Color(0xFF00E5FF), "replied to your comment")
        "follow" -> Triple(Icons.Default.PersonAdd, Color(0xFF4CAF50), "started following you")
        "save" -> Triple(Icons.Default.Bookmark, Color(0xFFFFD54F), "saved your video")
        else -> Triple(Icons.Default.Notifications, MaterialTheme.colorScheme.primary, n.type)
    }

    ListItem(
        modifier = Modifier.clickable {
            if (n.video != null) onVideoClick() else onProfileClick()
        },
        leadingContent = {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(n.fromUser, size = 48.dp, onClick = onProfileClick)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        },
        headlineContent = {
            androidx.compose.material3.Text(buildString {
                append(n.fromUser?.displayName ?: "Someone")
                append(" ")
                append(text)
                if (n.type == "comment" && n.video != null) append(": \"\"")
            }, fontWeight = if (!n.read) FontWeight.Bold else FontWeight.Normal)
        },
        supportingContent = { Text(Utils.timeAgo(n.createdAt), style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            n.video?.let { v ->
                Box(Modifier.size(52.dp, 72.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black).clickable(onClick = onVideoClick)) {
                    if (v.thumbnailUrl != null) {
                        AsyncImage(v.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    )
}
