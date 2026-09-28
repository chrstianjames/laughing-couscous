package com.shortly.app.ui.screens.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortly.app.data.model.Comment
import com.shortly.app.data.model.Video
import com.shortly.app.data.repository.VideoRepository
import com.shortly.app.ui.components.Avatar
import com.shortly.app.ui.components.EmptyState
import com.shortly.app.ui.components.ErrorState
import com.shortly.app.ui.components.LoadingState
import com.shortly.app.ui.util.Utils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    video: Video,
    onDismiss: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Comment?>(null) }
    var posting by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            VideoRepository.instance.getComments(video.id, 1, 100).onSuccess {
                comments = it.items
                error = null
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(video.id) { load() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            // Header
            Surface(shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${video.commentsCount} comments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
            }
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                when {
                    loading -> LoadingState()
                    error != null -> ErrorState(error ?: "Error", onRetry = { load() })
                    comments.isEmpty() -> EmptyState("No comments yet", "Be the first to comment")
                    else -> {
                        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(comments, key = { it.id }) { c ->
                                CommentItem(
                                    comment = c,
                                    onProfileClick = onProfileClick,
                                    onLike = {
                                        scope.launch {
                                            VideoRepository.instance.toggleLikeComment(c).onSuccess { upd ->
                                                comments = comments.toMutableList().apply {
                                                    val i = indexOfFirst { it.id == upd.id }
                                                    if (i >= 0) set(i, upd)
                                                }
                                            }
                                        }
                                    },
                                    onReply = { replyingTo = it }
                                )
                            }
                        }
                    }
                }
            }

            // Reply indicator
            if (replyingTo != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Replying to @${replyingTo?.user?.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { replyingTo = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Input
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(if (replyingTo != null) "Reply..." else "Add a comment...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isBlank()) return@IconButton
                            posting = true
                            scope.launch {
                                VideoRepository.instance.addComment(
                                    video.id,
                                    input.trim(),
                                    replyingTo?.id
                                ).onSuccess { c ->
                                    if (replyingTo == null) {
                                        comments = listOf(c) + comments
                                    } else {
                                        // Increment reply count
                                        comments = comments.toMutableList().apply {
                                            val i = indexOfFirst { it.id == replyingTo!!.id }
                                            if (i >= 0) set(i, get(i).copy(replyCount = get(i).replyCount + 1))
                                        }
                                    }
                                    input = ""
                                    replyingTo = null
                                }
                                posting = false
                            }
                        },
                        enabled = input.isNotBlank() && !posting,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (posting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Send, contentDescription = "Post")
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    onProfileClick: (String) -> Unit,
    onLike: () -> Unit,
    onReply: (Comment) -> Unit
) {
    var showReplies by remember { mutableStateOf(false) }
    var replies by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loadingReplies by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadReplies() {
        scope.launch {
            loadingReplies = true
            VideoRepository.instance.getReplies(comment.id, 1, 50).onSuccess { replies = it.items }
            loadingReplies = false
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(comment.user, size = 36.dp, onClick = { comment.user?.username?.let(onProfileClick) })
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.user?.username ?: "user", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Text(Utils.timeAgo(comment.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onReply(comment) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Reply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
                if (comment.replyCount > 0) {
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = {
                        showReplies = !showReplies
                        if (showReplies && replies.isEmpty()) loadReplies()
                    }, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            "— ${if (showReplies) "Hide" else "View"} ${comment.replyCount} ${if (comment.replyCount == 1) "reply" else "replies"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (showReplies) {
                Spacer(Modifier.height(4.dp))
                if (loadingReplies) {
                    CircularProgressIndicator(Modifier.size(18.dp).padding(4.dp), strokeWidth = 2.dp)
                } else {
                    replies.forEach { r ->
                        Row(Modifier.padding(top = 8.dp)) {
                            Avatar(r.user, size = 28.dp, onClick = { r.user?.username?.let(onProfileClick) })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(r.user?.username ?: "user", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(r.text, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Utils.timeAgo(r.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    IconButton(onClick = {
                                        scope.launch {
                                            VideoRepository.instance.toggleLikeComment(r).onSuccess { upd ->
                                                replies = replies.toMutableList().apply {
                                                    val i = indexOfFirst { it.id == upd.id }
                                                    if (i >= 0) set(i, upd)
                                                }
                                            }
                                        }
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            if (r.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (r.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(if (r.likesCount > 0) Utils.formatCount(r.likesCount) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
            IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (comment.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(Utils.formatCount(comment.likesCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Full-screen Comments screen (when navigating to comment via nav graph)
@Composable
fun CommentsScreen(
    videoId: String,
    onProfileClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var video by remember { mutableStateOf<Video?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId) {
        VideoRepository.instance.getVideo(videoId).onSuccess { video = it }
            .onFailure { error = it.message }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Comments") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            video?.let { v ->
                // Reuse CommentsSheet content but full screen - simpler: show list directly
                CommentsFullList(v, onProfileClick)
            }
            if (error != null) ErrorState(error ?: "Error")
        }
    }
}

@Composable
fun CommentsFullList(video: Video, onProfileClick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            VideoRepository.instance.getComments(video.id, 1, 100).onSuccess { comments = it.items }
            loading = false
        }
    }
    LaunchedEffect(video.id) { load() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                loading -> LoadingState()
                comments.isEmpty() -> EmptyState("No comments yet")
                else -> LazyColumn {
                    items(comments, key = { it.id }) { c ->
                        CommentItem(comment = c, onProfileClick = onProfileClick, onLike = {
                            scope.launch {
                                VideoRepository.instance.toggleLikeComment(c).onSuccess { upd ->
                                    comments = comments.toMutableList().apply {
                                        val i = indexOfFirst { it.id == upd.id }
                                        if (i >= 0) set(i, upd)
                                    }
                                }
                            }
                        }, onReply = {})
                    }
                }
            }
        }
        Surface(shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Add a comment") }, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = input.isNotBlank() && !posting,
                    onClick = {
                        posting = true
                        scope.launch {
                            VideoRepository.instance.addComment(video.id, input.trim(), null).onSuccess { c ->
                                comments = listOf(c) + comments
                                input = ""
                            }
                            posting = false
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                ) {
                    if (posting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Default.Send, contentDescription = null)
                }
            }
        }
    }
}
