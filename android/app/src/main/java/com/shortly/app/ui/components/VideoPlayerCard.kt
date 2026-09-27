package com.shortly.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.shortly.app.data.model.Video
import com.shortly.app.ui.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerCard(
    video: Video,
    isVisible: Boolean,
    onLike: (Video) -> Unit = {},
    onComment: (Video) -> Unit = {},
    onShare: (Video) -> Unit = {},
    onSave: (Video) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onMore: (Video) -> Unit = {},
    onToggleFollow: (Video) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var player: Player? by remember { mutableStateOf(null) }
    val isPlaying by PlayerPool.isPlaying.collectAsState()
    val isMuted by PlayerPool.isMuted.collectAsState()
    val progress by PlayerPool.progress.collectAsState()
    val playerState by PlayerPool.playerState.collectAsState()

    var showPlayIcon by remember { mutableStateOf(false) }

    // Acquire player when visible
    DisposableEffect(isVisible, video.videoUrl) {
        if (isVisible) {
            val p = PlayerPool.acquire(context, video.videoUrl, playWhenReady = true)
            player = p
        } else {
            PlayerPool.pause()
            player = null
        }
        onDispose {
            // Don't release here - pool handles it when next video is acquired
            PlayerPool.pause()
        }
    }

    // Pause/resume based on visibility
    LaunchedEffect(isVisible) {
        if (isVisible) {
            PlayerPool.resume()
        } else {
            PlayerPool.pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        PlayerPool.togglePlay()
                        showPlayIcon = true
                        scope.launch {
                            delay(600)
                            showPlayIcon = false
                        }
                    },
                    onDoubleTap = {
                        if (!video.isLiked) onLike(video)
                    }
                )
            }
    ) {
        // Poster frame: always drawn underneath the player so the user sees
        // the thumbnail (not a black surface) while the video buffers, and
        // it stays visible if playback fails.
        val currentPlayer = player
        val showPoster = currentPlayer == null || playerState !is PlayerPool.PlayerState.Ready
        if (video.thumbnailUrl != null) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0C)))
        }

        // Video player
        if (currentPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        // Transparent shutter so the poster above shows
                        // through until the first frame is rendered.
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setKeepContentOnPlayerReset(true)
                        player = currentPlayer
                    }
                },
                update = { view ->
                    if (view.player !== currentPlayer) {
                        view.player = currentPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading overlay
        if (showPoster && playerState !is PlayerPool.PlayerState.Error && currentPlayer != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            }
        }

        // Error overlay with retry (previously errors were swallowed and the
        // card simply stayed black)
        (playerState as? PlayerPool.PlayerState.Error)?.let { err ->
            if (currentPlayer != null) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Couldn't play this video",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        err.message,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { PlayerPool.retry() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Retry") }
                }
            }
        }

        // Play icon overlay when tapped
        AnimatedVisibility(
            visible = showPlayIcon,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Bottom gradient for text readability
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(250.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = 0f
                    )
                )
        )

        // Top gradient for status bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        // Progress bar at bottom
        if (progress.second > 0) {
            LinearProgressIndicator(
                progress = { progress.first.toFloat() / progress.second.toFloat() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        }

        // Right action bar
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Avatar with follow button
            Box(contentAlignment = Alignment.BottomCenter) {
                Avatar(
                    user = video.author,
                    size = 50.dp,
                    onClick = { onProfileClick(video.author?.username ?: video.userId) }
                )
                if (!video.isFollowingAuthor && video.author?.id != com.shortly.app.data.repository.UserRepository.instance.currentUser.value?.id) {
                    IconButton(
                        onClick = { onToggleFollow(video) },
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Follow", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Like
            ActionButton(
                icon = if (video.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                count = Utils.formatCount(video.likesCount),
                tint = if (video.isLiked) Color(0xFFFF2E7D) else Color.White,
                onClick = { onLike(video) }
            )

            // Comment
            ActionButton(
                icon = Icons.Default.ChatBubbleOutline,
                count = Utils.formatCount(video.commentsCount),
                onClick = { onComment(video) }
            )

            // Save
            ActionButton(
                icon = if (video.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                count = Utils.formatCount(video.savesCount),
                tint = if (video.isSaved) Color(0xFFFFD54F) else Color.White,
                onClick = { onSave(video) }
            )

            // Mute
            IconButton(onClick = { PlayerPool.toggleMute() }) {
                Icon(
                    if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Share
            IconButton(onClick = { onShare(video) }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            // More
            IconButton(onClick = { onMore(video) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // Bottom info: username, caption
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${video.author?.username ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onProfileClick(video.author?.username ?: video.userId) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (!video.isFollowingAuthor && video.author?.id != com.shortly.app.data.repository.UserRepository.instance.currentUser.value?.id) {
                    TextButton(
                        onClick = { onToggleFollow(video) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                    ) {
                        Text("Follow", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            CaptionText(
                text = video.caption,
                onHashtagClick = onHashtagClick
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = Utils.formatCount(video.views) + " views",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                video.duration.takeIf { it > 0 }?.let {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = Utils.formatDuration((it * 1000).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        }
        Text(count, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaptionText(
    text: String,
    onHashtagClick: (String) -> Unit,
    maxLines: Int = 3
) {
    var expanded by remember { mutableStateOf(false) }
    val parts = remember(text) {
        val regex = Regex("""#(\w+)""")
        val annotated = buildAnnotatedString {
            var last = 0
            regex.findAll(text).forEach { match ->
                append(text.substring(last, match.range.first))
                pushStringAnnotation(tag = "hashtag", annotation = match.groupValues[1])
                withStyle(SpanStyle(color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)) {
                    append(match.value)
                }
                pop()
                last = match.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
        annotated
    }
    ClickableText(
        text = parts,
        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
        maxLines = if (expanded) Int.MAX_VALUE else maxLines,
        overflow = TextOverflow.Ellipsis,
        onClick = { offset ->
            parts.getStringAnnotations(tag = "hashtag", start = offset, end = offset).firstOrNull()?.let {
                onHashtagClick(it.item)
            } ?: run {
                expanded = !expanded
            }
        }
    )
}
