# Shortly - Short Video Social App

A complete, production-ready TikTok-style short-video social media Android app with a PHP backend.

## Features

### Android App (Native Kotlin + Jetpack Compose)
- **Authentication**: Sign up, Login, Logout with secure token-based auth
- **Full-screen vertical video feed** with swipe navigation, auto-play/pause, mute toggle
- **Video playback** via Android Media3/ExoPlayer with caching, byte-range streaming
- **Social**: Likes, Comments, Comment replies, Comment likes, Saves/Favorites, Shares
- **Profiles**: Avatars, Bios, Edit profile, Follow/Unfollow, Followers/Following lists
- **Feeds**: For You, Following
- **Search**: Users, Videos, Hashtags
- **Notifications**: Likes, comments, follows, replies
- **Upload**: Video upload with captions and hashtags
- **Moderation**: Report, Block, Delete own videos
- **Settings**: Dark/Light/Auto mode
- **UX**: Pull-to-refresh, infinite scroll, loading/empty/error states
- **Architecture**: MVVM/Clean Architecture, Coroutines, Coil for images
- **UI**: Material 3, responsive, dark/light mode
- **Performance**: Video caching, lazy loading, proper lifecycle management

### PHP Backend
- REST API at `/api`
- JSON-file database (no SQL required) with atomic file writes and file locking
- Pagination on all list endpoints
- Password hashing (`password_hash`/`password_verify`)
- Rate limiting on auth endpoints
- Secure file uploads with path traversal protection
- Video streaming with byte-range support
- Endpoints for auth, users, follows, videos, likes, comments, saves, notifications, search, reports, blocks

### Tech Stack
- **Android**: Kotlin, Jetpack Compose, Material 3, Media3/ExoPlayer, Retrofit/OkHttp, Coil, Coroutines, DataStore
- **Backend**: PHP with flock-based concurrent JSON file database
- **CI/CD**: GitHub Actions builds a signed release APK

## Building

The Android app is built automatically via GitHub Actions on every push. A release APK is produced as a downloadable artifact.

To build locally:
1. Install Android Studio (with Android SDK 34) and JDK 17
2. Open the `android/` directory in Android Studio
3. Run `./gradlew assembleRelease`

## Deployment

1. Upload the `api/` directory to a PHP web server (e.g., Apache or Nginx with PHP-FPM)
2. Make sure `api/data/` and `api/uploads/` are writable by the web server
3. The API base URL is configured in `android/app/src/main/java/com/shortly/app/util/Constants.kt`
4. The `.htaccess` rules protect database files and block PHP execution in uploads

## API Endpoints

All endpoints return JSON.

- `POST /api/auth/register` - Register
- `POST /api/auth/login` - Login
- `POST /api/auth/logout` - Logout
- `GET  /api/auth/me` - Current user
- `GET  /api/users/{username}` - Get user profile
- `POST /api/users/profile/edit` - Edit profile
- `POST /api/users/avatar` - Upload avatar
- `POST /api/users/{username}/follow` - Follow user
- `DELETE /api/users/{username}/follow` - Unfollow
- `GET  /api/users/{username}/followers` - List followers
- `GET  /api/users/{username}/following` - List following
- `POST /api/videos/upload` - Upload video (multipart)
- `GET  /api/videos/{id}` - Get video
- `DELETE /api/videos/{id}` - Delete own video
- `POST /api/videos/{id}/view` - Record view
- `POST /api/videos/{id}/like` - Like
- `DELETE /api/videos/{id}/like` - Unlike
- `POST /api/videos/{id}/save` - Save
- `DELETE /api/videos/{id}/save` - Unsave
- `GET  /api/feed/for-you` - For You feed
- `GET  /api/feed/following` - Following feed
- `GET  /api/videos/{id}/comments` - List comments
- `POST /api/videos/{id}/comments` - Add comment
- `POST /api/comments/{id}/like` - Like comment
- `GET  /api/comments/{id}/replies` - List replies
- `GET  /api/notifications` - List notifications
- `GET  /api/search?q=...` - Search
- `GET  /api/hashtags/trending` - Trending hashtags
- `POST /api/report` - Report content
- `POST /api/users/{username}/block` - Block user
