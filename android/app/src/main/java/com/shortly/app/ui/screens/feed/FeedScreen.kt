package com.shortly.app.ui.screens.feed

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shortly.app.data.model.Video
import com.shortly.app.data.repository.SocialRepository
import com.shortly.app.data.repository.VideoRepository
import com.shortly.app.ui.components.*
import com.shortly.app.ui.screens.comments.CommentsSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    feedType: String = "foryou",
    onProfileClick: (String) -> Unit,
    onCommentClick: (Video) -> Unit,
    onHashtagClick: (String) -> Unit,
    onFollowingTab: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var initialLoad by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf<Video?>(null) }
    var showMoreSheet by remember { mutableStateOf<Video?>(null) }

    val pagerState = rememberPagerState(pageCount = { videos.size })
    var currentVisibleIndex by remember { mutableStateOf(0) }

    fun loadMore(reset: Boolean = false) {
        scope.launch {
            if (reset) {
                page = 1
                hasMore = true
                videos = emptyList()
                error = null
            }
            if (!hasMore && !reset) return@launch
            try {
                val result = when (feedType) {
                    "following" -> VideoRepository.instance.getFollowing(page)
                    else -> VideoRepository.instance.getForYou(page)
                }
                result.onSuccess { paginated ->
                    videos = if (reset) paginated.items else videos + paginated.items
                    hasMore = paginated.hasMore
                    if (hasMore) page++
                    error = null
                }.onFailure {
                    error = it.message ?: "Failed to load feed"
                }
            } finally {
                loading = false
                refreshing = false
                initialLoad = false
            }
        }
    }

    LaunchedEffect(feedType) {
        loading = true
        loadMore(reset = true)
    }

    // Load more when close to end
    LaunchedEffect(pagerState.currentPage) {
        currentVisibleIndex = pagerState.currentPage
        if (pagerState.currentPage >= videos.size - 2 && hasMore && !loading) {
            loadMore()
        }
        // Mark video as viewed
        videos.getOrNull(pagerState.currentPage)?.let { v ->
            VideoRepository.instance.viewVideo(v.id)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (initialLoad && loading) {
            LoadingState()
        } else if (error != null && videos.isEmpty()) {
            ErrorState(message = error ?: "Failed to load", onRetry = { loadMore(reset = true) })
        } else if (videos.isEmpty()) {
            EmptyState(
                title = if (feedType == "following") "No videos yet" else "No videos",
                subtitle = if (feedType == "following") "Follow people to see their videos here" else "Be the first to upload a video!"
            )
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { videos[it].id }
            ) { index ->
                val video = videos[index]
                VideoPlayerCard(
                    video = video,
                    isVisible = index == currentVisibleIndex,
                    onLike = { v ->
                        scope.launch {
                            VideoRepository.instance.toggleLike(v).onSuccess { updated ->
                                videos = videos.toMutableList().apply {
                                    val i = indexOfFirst { it.id == updated.id }
                                    if (i >= 0) set(i, updated)
                                }
                            }
                        }
                    },
                    onComment = { showCommentsSheet = it },
                    onShare = { v -> shareVideo(context, v) },
                    onSave = { v ->
                        scope.launch {
                            VideoRepository.instance.toggleSave(v).onSuccess { updated ->
                                videos = videos.toMutableList().apply {
                                    val i = indexOfFirst { it.id == updated.id }
                                    if (i >= 0) set(i, updated)
                                }
                            }
                        }
                    },
                    onProfileClick = onProfileClick,
                    onHashtagClick = onHashtagClick,
                    onMore = { showMoreSheet = it },
                    onToggleFollow = { v ->
                        scope.launch {
                            VideoRepository.instance.toggleFollow(v).onSuccess { updated ->
                                videos = videos.toMutableList().apply {
                                    val i = indexOfFirst { it.id == updated.id }
                                    if (i >= 0) set(i, updated)
                                }
                            }
                        }
                    }
                )
            }

            // Top tabs (TikTok style: "Following | For You" with underline, search on the right)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 6.dp)
                    .height(44.dp)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    FeedTab(
                        title = "Following",
                        selected = feedType == "following",
                        onClick = { if (feedType != "following") onFollowingTab?.invoke() }
                    )
                    FeedTab(
                        title = "For You",
                        selected = feedType != "following",
                        onClick = { if (feedType == "following") onBack?.invoke() }
                    )
                }
                onSearchClick?.let {
                    IconButton(onClick = it, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }

        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 0.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    showCommentsSheet?.let { video ->
        CommentsSheet(
            video = video,
            onDismiss = { showCommentsSheet = null },
            onProfileClick = onProfileClick
        )
    }

    showMoreSheet?.let { video ->
        VideoMoreSheet(
            video = video,
            onDismiss = { showMoreSheet = null },
            onDelete = {
                scope.launch {
                    VideoRepository.instance.deleteVideo(video.id).onSuccess {
                        videos = videos.filter { it.id != video.id }
                        showMoreSheet = null
                    }
                }
            },
            onReport = { reason ->
                scope.launch {
                    SocialRepository.instance.report(videoId = video.id, reason = reason)
                    showMoreSheet = null
                }
            },
            onBlock = {
                scope.launch {
                    video.author?.username?.let { SocialRepository.instance.block(it) }
                    videos = videos.filter { it.id != video.id }
                    showMoreSheet = null
                }
            }
        )
    }
}

@Composable
private fun FeedTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            title,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(28.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) Color.White else Color.Transparent)
        )
    }
}

private fun shareVideo(context: Context, video: Video) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Check out this video on Shortly: ${video.caption.take(100)}\n${video.videoUrl}")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
}

@Composable
fun VideoMoreSheet(
    video: Video,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onReport: (String) -> Unit,
    onBlock: () -> Unit
) {
    val isOwn = video.author?.id == com.shortly.app.data.repository.UserRepository.instance.currentUser.value?.id
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                "More",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            if (isOwn) {
                TextButton(
                    onClick = { onDelete(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Delete video", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Report video")
                }
                TextButton(
                    onClick = { showBlockConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Block user", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
        }
    }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Why are you reporting?") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reason.isNotBlank()) onReport(reason)
                    showReportDialog = false
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block user?") },
            text = { Text("You won't see videos from this user.") },
            confirmButton = {
                TextButton(onClick = { onBlock(); showBlockConfirm = false }) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
