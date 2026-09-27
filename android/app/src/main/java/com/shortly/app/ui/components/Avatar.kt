package com.shortly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shortly.app.data.model.User

@Composable
fun Avatar(
    user: User?,
    size: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    showStroke: Boolean = false
) {
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (user?.avatarUrl != null) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val letter = user?.displayName?.firstOrNull()?.uppercase() ?: user?.username?.firstOrNull()?.uppercase() ?: "?"
            Text(
                text = letter,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (showStroke) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0f))
            )
        }
    }
}
