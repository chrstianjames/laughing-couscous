package com.shortly.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import coil3.compose.AsyncImage
import com.shortly.app.data.model.User
import com.shortly.app.data.repository.UserRepository
import com.shortly.app.ui.components.Avatar
import com.shortly.app.ui.components.LoadingState
import com.shortly.app.platform.PickedFile
import com.shortly.app.platform.rememberFilePicker
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentUser by UserRepository.instance.currentUser.collectAsState()
    var displayName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var username by remember { mutableStateOf(currentUser?.username ?: "") }
    var bio by remember { mutableStateOf(currentUser?.bio ?: "") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var avatarFile by remember { mutableStateOf<PickedFile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            displayName = currentUser!!.displayName
            username = currentUser!!.username
            bio = currentUser!!.bio
            loading = false
        }
    }

    val pickAvatar = rememberFilePicker("image/*") { file -> file?.let { avatarFile = it } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(
                        onClick = {
                            if (displayName.isBlank()) { error = "Display name required"; return@TextButton }
                            saving = true
                            scope.launch {
                                // Upload avatar first if changed
                                avatarFile?.let { file ->
                                    UserRepository.instance.uploadAvatar(file).onFailure { error = it.message }
                                }
                                val result = UserRepository.instance.editProfile(
                                    displayName = displayName.trim(),
                                    bio = bio.trim(),
                                    username = username.trim().takeIf { it != currentUser?.username }
                                )
                                saving = false
                                result.onSuccess { onDone() }
                                result.onFailure { error = it.message }
                            }
                        },
                        enabled = !saving
                    ) { Text("Save", fontWeight = FontWeight.Bold) }
                }
            )
        }
    ) { padding ->
        if (currentUser == null) {
            LoadingState(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (avatarFile != null) {
                        AsyncImage(
                            model = avatarFile!!.previewUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    } else {
                        Avatar(currentUser, size = 100.dp)
                    }
                    IconButton(
                        onClick = { pickAvatar() },
                        modifier = Modifier.offset(x = (-4).dp, y = (-4).dp).size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; error = null },
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it; error = null },
                    label = { Text("Bio") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (saving) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}
