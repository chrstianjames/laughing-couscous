package com.shortly.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.shortly.app.data.model.Video
import com.shortly.app.data.repository.UserRepository
import com.shortly.app.ui.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TikTokRed = Color(0xFFFE2C55)
private val TikTokYellow = Color(0xFFFACE15)

/** One floating heart spawned by a double tap. */
private data class HeartBurst(val id: Long, val position: Offset, val rotation: Float)

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

    var showPauseIcon by remember { mutableStateOf(false) }
    var hearts by remember { mutableStateOf(listOf<HeartBurst>()) }

    val currentUserId = UserRepository.instance.currentUser.collectAsState().value?.id
    val isOwnVideo = video.author?.id != null && video.author.id == currentUserId

    // Acquire player when visible
    DisposableEffect(isVisible, video.videoUrl) {
        if (isVisible) {
            player = PlayerPool.acquire(context, video.videoUrl, playWhenReady = true)
        } else {
            PlayerPool.pause()
            player = null
        }
        onDispose { PlayerPool.pause() }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) PlayerPool.resume() else PlayerPool.pause()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(video.id) {
                detectTapGestures(
                    onTap = {
                        PlayerPool.togglePlay()
                        showPauseIcon = true
                        scope.launch { delay(700); showPauseIcon = false }
                    },
                    onDoubleTap = { pos ->
                        // TikTok: double tap always shows a heart, and likes if not liked yet
                        val burst = HeartBurst(System.nanoTime(), pos, (-25..25).random().toFloat())
                        hearts = hearts + burst
                        scope.launch { delay(900); hearts = hearts - burst }
                        if (!video.isLiked) onLike(video)
                    },
                    onLongPress = { onMore(video) }
                )
            }
    ) {
        // ---------- Video / poster ----------
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
        if (currentPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setKeepContentOnPlayerReset(true)
                        player = currentPlayer
                    }
                },
                update = { view -> if (view.player !== currentPlayer) view.player = currentPlayer },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showPoster && playerState !is PlayerPool.PlayerState.Error && currentPlayer != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            }
        }
        (playerState as? PlayerPool.PlayerState.Error)?.let { err ->
            if (currentPlayer != null) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Couldn't play this video", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(err.message, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { PlayerPool.retry() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Retry") }
                }
            }
        }

        // ---------- Pause icon (TikTok shows a big translucent play arrow when paused) ----------
        AnimatedVisibility(
            visible = showPauseIcon || (!isPlaying && playerState is PlayerPool.PlayerState.Ready),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(96.dp)
            )
        }

        // ---------- Double-tap hearts ----------
        hearts.forEach { h -> FloatingHeart(h) }

        // ---------- Gradients ----------
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(280.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
        )

        // ---------- Right sidebar ----------
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Avatar with red "+" follow badge
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.padding(bottom = 6.dp)) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { onProfileClick(video.author?.username ?: video.userId) }
                ) {
                    Avatar(user = video.author, size = 50.dp)
                }
                if (!isOwnVideo) {
                    FollowBadge(
                        following = video.isFollowingAuthor,
                        onClick = { onToggleFollow(video) },
                        modifier = Modifier.offset(y = 10.dp)
                    )
                }
            }

            SideAction(
                icon = Icons.Default.Favorite,
                label = Utils.formatCount(video.likesCount),
                tint = if (video.isLiked) TikTokRed else Color.White,
                animateKey = video.isLiked,
                onClick = { onLike(video) }
            )
            SideAction(
                icon = Icons.Default.ChatBubble,
                label = Utils.formatCount(video.commentsCount),
                onClick = { onComment(video) }
            )
            SideAction(
                icon = Icons.Default.Bookmark,
                label = Utils.formatCount(video.savesCount),
                tint = if (video.isSaved) TikTokYellow else Color.White,
                animateKey = video.isSaved,
                onClick = { onSave(video) }
            )
            SideAction(
                icon = Icons.Default.Reply,
                label = "Share",
                mirror = true,
                onClick = { onShare(video) }
            )

            // Spinning music disc
            MusicDisc(video = video, spinning = isPlaying && isVisible)
        }

        // ---------- Mute toggle (top-right, under the tabs) ----------
        IconButton(
            onClick = { PlayerPool.toggleMute() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 52.dp, end = 4.dp)
        ) {
            Icon(
                if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Mute",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
        }

        // ---------- Bottom info ----------
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 84.dp, bottom = 14.dp)
        ) {
            Text(
                text = "@" + (video.author?.username ?: "user"),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.clickable { onProfileClick(video.author?.username ?: video.userId) }
            )
            Spacer(Modifier.height(6.dp))
            CaptionText(text = video.caption, onHashtagClick = onHashtagClick)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                MarqueeText(
                    text = "original sound - " + (video.author?.displayName ?: video.author?.username ?: "user"),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                Text(Utils.formatCount(video.views), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }

        // ---------- Progress bar ----------
        if (progress.second > 0) {
            LinearProgressIndicator(
                progress = { progress.first.toFloat() / progress.second.toFloat() },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun FollowBadge(following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Red "+" that flips to a check and fades out once followed (like TikTok)
    val visible = remember { mutableStateOf(true) }
    LaunchedEffect(following) {
        if (following) { delay(1200); visible.value = false } else visible.value = true
    }
    AnimatedVisibility(visible = visible.value, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (following) Color.White else TikTokRed)
                .clickable(enabled = !following) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (following) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (following) "Following" else "Follow",
                tint = if (following) TikTokRed else Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun SideAction(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    mirror: Boolean = false,
    animateKey: Boolean = false,
    onClick: () -> Unit
) {
    // Bounce when toggled on
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(animateKey) {
        if (first) { first = false; return@LaunchedEffect }
        if (animateKey) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(34.dp)
                .scale(scale.value)
                .graphicsLayer { if (mirror) scaleX = -1f }
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MusicDisc(video: Video, spinning: Boolean) {
    val transition = rememberInfiniteTransition(label = "disc")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "angle"
    )
    var held by remember { mutableStateOf(0f) }
    if (!spinning) held = angle
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(46.dp)
            .rotate(if (spinning) angle else held)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF111111)))),
        contentAlignment = Alignment.Center
    ) {
        Avatar(user = video.author, size = 24.dp)
    }
}

@Composable
private fun FloatingHeart(h: HeartBurst) {
    val density = LocalDensity.current
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(h.id) {
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        launch { rise.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        delay(350)
        alpha.animateTo(0f, tween(450))
    }
    val size = 100.dp
    val sizePx = with(density) { size.toPx() }
    val yOffset = with(density) { (rise.value * 120.dp.toPx()) }
    Icon(
        Icons.Default.Favorite,
        contentDescription = null,
        tint = TikTokRed,
        modifier = Modifier
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    (h.position.x - sizePx / 2).toInt(),
                    (h.position.y - sizePx / 2 - yOffset).toInt()
                )
            }
            .size(size)
            .rotate(h.rotation)
            .scale(scale.value)
            .graphicsLayer { this.alpha = alpha.value }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE)
    )
}

@Composable
private fun CaptionText(
    text: String,
    onHashtagClick: (String) -> Unit,
    maxLines: Int = 2
) {
    if (text.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    val parts = remember(text) {
        val regex = Regex("""#(\w+)""")
        buildAnnotatedString {
            var last = 0
            regex.findAll(text).forEach { match ->
                append(text.substring(last, match.range.first))
                pushStringAnnotation(tag = "hashtag", annotation = match.groupValues[1])
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.value) }
                pop()
                last = match.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
    ClickableText(
        text = parts,
        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 14.sp),
        maxLines = if (expanded) Int.MAX_VALUE else maxLines,
        overflow = TextOverflow.Ellipsis,
        onClick = { offset ->
            parts.getStringAnnotations(tag = "hashtag", start = offset, end = offset).firstOrNull()?.let {
                onHashtagClick(it.item)
            } ?: run { expanded = !expanded }
        }
    )
}
