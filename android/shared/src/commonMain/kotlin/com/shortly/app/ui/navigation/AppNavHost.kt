package com.shortly.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shortly.app.platform.videoBackdropColor
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shortly.app.ui.components.PlayerPool
import com.shortly.app.ui.screens.auth.LoginScreen
import com.shortly.app.ui.screens.auth.SignupScreen
import com.shortly.app.ui.screens.comments.CommentsScreen
import com.shortly.app.ui.screens.feed.FeedScreen
import com.shortly.app.ui.screens.notifications.NotificationsScreen
import com.shortly.app.ui.screens.profile.*
import com.shortly.app.ui.screens.search.HashtagScreen
import com.shortly.app.ui.screens.search.SearchScreen
import com.shortly.app.ui.screens.settings.SettingsScreen
import com.shortly.app.ui.screens.upload.UploadScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Login : Screen("login", "Login")
    object Signup : Screen("signup", "Sign Up")
    object Feed : Screen("feed", "For You", Icons.Default.Home)
    object Search : Screen("search", "Discover", Icons.Default.Search)
    object Upload : Screen("upload", "Upload", Icons.Default.AddBox)
    object Notifications : Screen("notifications", "Activity", Icons.Default.Notifications)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object EditProfile : Screen("edit_profile", "Edit Profile")
    object UserProfile : Screen("user_profile/{username}", "Profile") {
        fun createRoute(username: String) = "user_profile/$username"
    }
    object FollowList : Screen("follow_list/{username}/{type}", "Follow List") {
        fun createRoute(username: String, type: String) = "follow_list/$username/$type"
    }
    object Comments : Screen("comments/{videoId}", "Comments") {
        fun createRoute(videoId: String) = "comments/$videoId"
    }
    object Hashtag : Screen("hashtag/{tag}", "Hashtag") {
        fun createRoute(tag: String) = "hashtag/$tag"
    }
    object Settings : Screen("settings", "Settings")
    object Saved : Screen("saved", "Saved")
}

val bottomTabs = listOf(Screen.Feed, Screen.Search, Screen.Upload, Screen.Notifications, Screen.Profile)

@Composable
fun AppNavHost(startDestination: String = "auth") {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isFeedRoute = currentRoute == Screen.Feed.route || currentRoute == "following_feed"
    val showBottomBar = currentRoute in bottomTabs.map { it.route } || isFeedRoute

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // On the web the feed's <video> sits behind a transparent canvas, so
        // nothing opaque may be painted behind the feed.
        containerColor = if (isFeedRoute) videoBackdropColor else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                TikTokBottomBar(
                    currentRoute = if (isFeedRoute) Screen.Feed.route else currentRoute,
                    onFeed = isFeedRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startDestination == "main") Screen.Feed.route else Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Auth
            composable(Screen.Login.route) {
                LoginScreen(
                    onGoToSignup = { navController.navigate(Screen.Signup.route) },
                    onLoggedIn = {
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Signup.route) {
                SignupScreen(
                    onGoToLogin = { navController.popBackStack() },
                    onSignedUp = {
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // Main tabs
            composable(Screen.Feed.route) {
                FeedScreen(
                    feedType = "foryou",
                    onProfileClick = { username -> navController.navigate(Screen.UserProfile.createRoute(username)) },
                    onCommentClick = { video -> navController.navigate(Screen.Comments.createRoute(video.id)) },
                    onHashtagClick = { tag -> navController.navigate(Screen.Hashtag.createRoute(tag)) },
                    onFollowingTab = { navController.navigate("following_feed") },
                    onSearchClick = { navController.navigate(Screen.Search.route) }
                )
            }
            composable("following_feed") {
                FeedScreen(
                    feedType = "following",
                    onProfileClick = { username -> navController.navigate(Screen.UserProfile.createRoute(username)) },
                    onCommentClick = { video -> navController.navigate(Screen.Comments.createRoute(video.id)) },
                    onHashtagClick = { tag -> navController.navigate(Screen.Hashtag.createRoute(tag)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onProfileClick = { u -> navController.navigate(Screen.UserProfile.createRoute(u)) },
                    onHashtagClick = { t -> navController.navigate(Screen.Hashtag.createRoute(t)) },
                    onVideoClick = { v -> navController.navigate(Screen.Comments.createRoute(v.id)) }
                )
            }
            composable(Screen.Upload.route) {
                UploadScreen(onUploaded = {
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.Upload.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    onProfileClick = { u -> navController.navigate(Screen.UserProfile.createRoute(u)) },
                    onVideoClick = { v -> navController.navigate(Screen.Comments.createRoute(v.id)) }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    isCurrentUser = true,
                    onEditProfile = { navController.navigate(Screen.EditProfile.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    onFollowers = { u -> navController.navigate(Screen.FollowList.createRoute(u, "followers")) },
                    onFollowing = { u -> navController.navigate(Screen.FollowList.createRoute(u, "following")) },
                    onSaved = { navController.navigate(Screen.Saved.route) },
                    onLogout = {
                        PlayerPool.release()
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            // Saved videos
            composable(Screen.Saved.route) {
                UserVideosScreen(
                    title = "Saved Videos",
                    source = UserVideoSource.SAVED,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { v -> navController.navigate(Screen.Comments.createRoute(v.id)) }
                )
            }

            // Other
            composable(Screen.EditProfile.route) {
                EditProfileScreen(onDone = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Screen.UserProfile.route,
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { backStackEntry ->
                val username = backStackEntry.arguments?.getString("username") ?: ""
                ProfileScreen(
                    isCurrentUser = false,
                    username = username,
                    onBack = { navController.popBackStack() },
                    onFollowers = { u -> navController.navigate(Screen.FollowList.createRoute(u, "followers")) },
                    onFollowing = { u -> navController.navigate(Screen.FollowList.createRoute(u, "following")) }
                )
            }

            composable(
                Screen.FollowList.route,
                arguments = listOf(
                    navArgument("username") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val username = backStackEntry.arguments?.getString("username") ?: ""
                val type = backStackEntry.arguments?.getString("type") ?: "followers"
                FollowListScreen(
                    username = username,
                    type = type,
                    onBack = { navController.popBackStack() },
                    onUserClick = { u -> navController.navigate(Screen.UserProfile.createRoute(u)) }
                )
            }

            composable(
                Screen.Comments.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                CommentsScreen(
                    videoId = videoId,
                    onProfileClick = { u -> navController.navigate(Screen.UserProfile.createRoute(u)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                Screen.Hashtag.route,
                arguments = listOf(navArgument("tag") { type = NavType.StringType })
            ) { backStackEntry ->
                val tag = backStackEntry.arguments?.getString("tag") ?: ""
                HashtagScreen(
                    tag = tag,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { v -> navController.navigate(Screen.Comments.createRoute(v.id)) }
                )
            }
        }
    }
}

/**
 * TikTok-style bottom bar: black on the feed (white/theme elsewhere), five
 * slots with the big "+" create button in the middle (white pill with the
 * cyan / red offset backplates).
 */
@Composable
fun TikTokBottomBar(
    currentRoute: String?,
    onFeed: Boolean,
    onNavigate: (Screen) -> Unit
) {
    val bg = if (onFeed) Color.Black else MaterialTheme.colorScheme.surface
    val fg = if (onFeed) Color.White else MaterialTheme.colorScheme.onSurface
    Column(Modifier.background(bg)) {
        if (!onFeed) HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { screen ->
                val selected = currentRoute == screen.route
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onNavigate(screen) },
                    contentAlignment = Alignment.Center
                ) {
                    if (screen == Screen.Upload) {
                        CreateButton()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val icon = when (screen) {
                                Screen.Feed -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                                Screen.Search -> Icons.Outlined.Explore
                                Screen.Notifications -> if (selected) Icons.Filled.Inbox else Icons.Outlined.Inbox
                                Screen.Profile -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                                else -> screen.icon ?: Icons.Filled.Circle
                            }
                            Icon(
                                icon,
                                contentDescription = screen.title,
                                tint = if (selected) fg else fg.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when (screen) {
                                    Screen.Feed -> "Home"
                                    Screen.Search -> "Discover"
                                    Screen.Notifications -> "Inbox"
                                    Screen.Profile -> "Me"
                                    else -> screen.title
                                },
                                color = if (selected) fg else fg.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun CreateButton() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 48.dp, height = 30.dp)) {
        Box(
            Modifier
                .offset(x = (-4).dp)
                .size(width = 38.dp, height = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF20D5EC))
        )
        Box(
            Modifier
                .offset(x = 4.dp)
                .size(width = 38.dp, height = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFE2C55))
        )
        Box(
            Modifier
                .size(width = 38.dp, height = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create", tint = Color.Black, modifier = Modifier.size(22.dp))
        }
    }
}
