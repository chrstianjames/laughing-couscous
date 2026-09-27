# Shortly — Short-Video Social App

A complete, production-ready short-video social media Android app — built with native
Kotlin + Jetpack Compose (**no Flutter/React Native/WebView**) — paired with a PHP
REST backend that uses a JSON-file database (no MySQL/PostgreSQL/SQLite).

## Project Structure

```
android/   Native Kotlin + Jetpack Compose + Material 3 Android app
api/       PHP 8 REST API with flock()-based JSON-file database
.setup/    GitHub Actions workflow (copy into .github/workflows/ to enable)
BUILD.md   Build & deployment instructions
```

## Features

### Android App (Native Kotlin / Jetpack Compose / Material 3)
- Sign up, Login, Logout with secure token auth
- User profiles, avatars, bio, edit profile
- Follow / Unfollow, Followers / Following lists
- Full-screen vertical video feed with swipe, auto-play/pause, mute toggle
- Video playback via Android **Media3 / ExoPlayer** with on-disk caching, byte-range streaming
- Likes, comments, comment replies, comment likes, saves/favorites, share
- For You & Following feeds, pull-to-refresh, infinite scrolling
- Search users, videos, hashtags — with trending hashtags
- Video upload with captions and hashtags
- Video view counter
- Notifications / Activity feed
- Delete own videos, report videos, report users, block users
- Dark / light / system theme
- Loading / empty / error / offline states
- MVVM-style architecture, coroutines, Coil for images, Retrofit/OkHttp, DataStore
- Proper lifecycle / memory management, lazy-loaded media, efficient play/pause

### PHP Backend (REST API)
- Endpoints for auth, users, profiles, follows, videos, uploads, feed, likes,
  comments, replies, saves, notifications, search, reports, blocks, sessions
- `password_hash()` / `password_verify()`, Authorization checks
- JSON-file database with `flock()`, atomic writes, unique IDs, pagination
- Upload validation, size limits, path-traversal protection
- `.htaccess` rules protect DB files & disable PHP execution in uploads
- Rate limiting on auth endpoints
- Video streaming with HTTP byte-range support (seeking)
- Pagination, safe JSON responses, proper HTTP status codes

### API Base URL
`https://app.chinai.uk/api` (configured in `android/app/.../util/Constants.kt`)

## Build the APK

The project is set up to build a release APK via **GitHub Actions**. Because the
automation token used to push this branch doesn't have GitHub's `workflows` scope,
the workflow YAML lives at `.setup/android-build.yml`. To enable CI builds:

1. Open this repo on GitHub
2. Click **Add file → Create new file**
3. Name it exactly: `.github/workflows/android-build.yml`
4. Paste the contents of `.setup/android-build.yml` (already in this repo) and commit

On every subsequent push GitHub Actions will:
- set up JDK 17 and Android SDK 34
- run `./gradlew assembleRelease`
- upload a signed release APK as a downloadable artifact on the Actions run

For local builds (Android Studio / JDK 17 + Android SDK 34):
```bash
cd android
./gradlew assembleRelease
```
APK output: `android/app/build/outputs/apk/release/`.

See **BUILD.md** for full instructions.

## Deploying the PHP API

Upload the entire `api/` directory to any PHP 8+ web host:
- Make `api/data/`, `api/uploads/videos/`, `api/uploads/avatars/` writable by the web server
- `.htaccess` rules protect database files and block script execution in uploads
- Files are stored under `api/uploads/`; JSON DB lives in `api/data/`

## Tech Stack

| Layer | Technologies |
|-------|--------------|
| Android | Kotlin, Jetpack Compose, Material 3, Media3/ExoPlayer, Retrofit/OkHttp, Coil, Coroutines, DataStore, Navigation Compose, Paging |
| Backend | PHP 8, `flock()` JSON database, byte-range video streaming, bcrypt password hashing |
| CI/CD | GitHub Actions (workflow template in `.setup/`) |
