# Shortly — Short-Video Social App

A complete, production-ready short-video social media Android app built with
**native Kotlin + Jetpack Compose** (no Flutter / React Native / WebView)
paired with a PHP REST backend that uses a JSON-file database (no SQL).

PR #1 has been **merged into `main`**. The code is live on the default branch.

## APK builds on GitHub Actions

> ### ⚠️ One manual step is still required
>
> GitHub refuses to let the Arena coding agent write to `.github/workflows/**`
> (`refusing to allow a GitHub App to create or update workflow ... without
> 'workflows' permission`), so the **fixed** workflow is committed here instead:
>
> **`.setup/android-build.yml`**
>
> Apply it in one of three ways — see [`.setup/APPLY-WORKFLOW.md`](.setup/APPLY-WORKFLOW.md):
>
> * **Grant the permission** — *Settings → GitHub Apps → Arena AI Coding Agent →
>   Configure → Repository permissions → Workflows → Read and write*, then ask the
>   agent to push it. Everything else is already done.
> * **Paste it yourself** — open
>   [`.github/workflows/android-build.yml`](https://github.com/chrstianjames/laughing-couscous/edit/main/.github/workflows/android-build.yml),
>   select all, delete, paste the contents of
>   [`.setup/android-build.yml`](.setup/android-build.yml), and commit to `main`.
>
> Until that happens the Actions run still fails in **Setup Android SDK** with
> `Warning: Failed to find package 'tools'` — that step comes from
> `android-actions/setup-android@v3`, which is removed in the fixed version.

Once applied, the workflow at `.github/workflows/android-build.yml` runs on every
push and pull request to `main` (and on demand via *Run workflow*):

1. Sets up JDK 17 (Temurin) and the Android SDK 34 platform + build-tools 34.0.0
2. Runs `./gradlew assembleRelease` in `android/` using the committed Gradle 8.5 wrapper
3. Signs the APK (temporary CI key by default — see below)
4. Uploads **`shortly-release-apk`** as an artifact on the [Actions tab](https://github.com/chrstianjames/laughing-couscous/actions)

### Signing with your own release key (optional)

By default CI generates a throwaway keystore so the artifact is installable for testing.
To publish with a real key, add these four repository secrets
(*Settings → Secrets and variables → Actions*):

| Secret | Contents |
| --- | --- |
| `SHORTLY_KEYSTORE_BASE64` | `base64 -w0 your-release-key.jks` |
| `SHORTLY_STORE_PASSWORD` | keystore password |
| `SHORTLY_KEY_ALIAS` | key alias |
| `SHORTLY_KEY_PASSWORD` | key password |

The workflow picks them up automatically — no other change needed.

## Project structure

```
android/    Native Kotlin + Jetpack Compose + Material 3 Android app
api/        PHP 8 REST API with flock()-based JSON-file database
.setup/     Reference copy of the CI workflow
BUILD.md    Full build & deployment instructions
```

## Features

### Android (Kotlin / Jetpack Compose / Material 3 / MVVM)
- Sign up, login, logout — token-based auth
- Profiles with avatars, bio, edit profile
- Follow / Unfollow, Followers / Following lists
- Full-screen vertical video feed (swipe to change, auto-play/pause, mute)
- Media3/ExoPlayer playback with on-disk caching, byte-range streaming
- Likes, comments (with replies & likes), saves/favorites, share, view counter
- For You & Following feeds, infinite scroll, pull-to-refresh
- Search users / videos / hashtags, trending hashtags, hashtag pages
- Video upload with captions and hashtags
- Notifications / activity feed
- Delete own videos, report, block
- Dark / Light / system theme
- Loading / empty / error / offline states
- Coil image loading, Retrofit/OkHttp, DataStore, proper lifecycle cleanup

### PHP Backend (api/)
- All required REST endpoints (auth, users, profiles, follows, videos, uploads,
  feed, likes, comments, replies, saves, notifications, search, hashtags,
  reports, blocks, sessions)
- JSON-file database using `flock()` + atomic writes, unique IDs, pagination
- `password_hash()` / `password_verify()`, authorization checks
- Upload validation (size, MIME, path-traversal protection)
- `.htaccess` protects DB files, blocks PHP in uploads
- Rate limiting on auth endpoints
- Video streaming with HTTP byte-range support
- Safe JSON responses, proper HTTP status codes

### API Base URL
`https://app.chinai.uk/api` (configurable in
`android/app/src/main/java/com/shortly/app/util/Constants.kt`)

## Local build

```bash
cd android
./gradlew assembleRelease
```
Output APK: `android/app/build/outputs/apk/release/app-release.apk`
(debug-signed unless you configure a release keystore — see BUILD.md).
Requires JDK 17 and Android SDK 34 (Build-Tools 34.0.0). Android Studio works
out of the box. See BUILD.md for full details.

## Deploying the PHP API

Upload the entire `api/` directory to a PHP 8+ host and make `api/data/`,
`api/uploads/videos/`, and `api/uploads/avatars/` writable by the web server.
