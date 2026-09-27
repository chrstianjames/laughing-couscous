# Shortly — Short-Video Social App

A complete, production-ready short-video social media Android app built with
**native Kotlin + Jetpack Compose** (no Flutter / React Native / WebView)
paired with a PHP REST backend using a JSON-file database (no SQL).

## Quick start — get a release APK from GitHub Actions

The repo ships with a complete GitHub Actions workflow that builds a release
APK on every push. The automation token used to push this code cannot write
files under `.github/workflows/` directly, so to enable CI builds just open
this one-click link and press **Commit new file** (filename and contents are
pre-filled for you):

👉 **[Click here to enable GitHub Actions APK builds](https://github.com/chrstianjames/laughing-couscous/new/arena/01a0e134-laughing-couscous?filename=.github%2Fworkflows%2Fandroid-build.yml&value=name%3A+Build+Android+APK%0A%0Aon%3A%0A++push%3A%0A++++branches%3A+%5Bmain%2C+master%5D%0A++pull_request%3A%0A++++branches%3A+%5Bmain%2C+master%5D%0A++workflow_dispatch%3A%0A%0Ajobs%3A%0A++build%3A%0A++++name%3A+Build+Release+APK%0A++++runs-on%3A+ubuntu-latest%0A++++timeout-minutes%3A+45%0A%0A++++steps%3A%0A++++++-+name%3A+Checkout%0A++++++++uses%3A+actions%2Fcheckout%40v4%0A%0A++++++-+name%3A+Set+up+JDK+17%0A++++++++uses%3A+actions%2Fsetup-java%40v4%0A++++++++with%3A%0A++++++++++java-version%3A+%2717%27%0A++++++++++distribution%3A+%27temurin%27%0A%0A++++++-+name%3A+Setup+Gradle%0A++++++++uses%3A+gradle%2Factions%2Fsetup-gradle%40v4%0A++++++++with%3A%0A++++++++++gradle-version%3A+%278.5%27%0A++++++++++build-root-directory%3A+android%0A%0A++++++-+name%3A+Setup+Android+SDK%0A++++++++uses%3A+android-actions%2Fsetup-android%40v3%0A%0A++++++-+name%3A+Install+Android+SDK+components%0A++++++++shell%3A+bash%0A++++++++run%3A+%7C%0A++++++++++set+-x%0A++++++++++SDKMAN%3D%22%24ANDROID_SDK_ROOT%2Fcmdline-tools%2Flatest%2Fbin%2Fsdkmanager%22%0A++++++++++if+%5B+%21+-x+%22%24SDKMAN%22+%5D%3B+then%0A++++++++++++SDKMAN%3D%24%28find+%22%24ANDROID_SDK_ROOT%2Fcmdline-tools%22+-name+sdkmanager+-type+f+2%3E%2Fdev%2Fnull+%7C+head+-1%29%0A++++++++++fi%0A++++++++++if+%5B+-z+%22%24SDKMAN%22+%5D%3B+then%0A++++++++++++cd+%22%24ANDROID_SDK_ROOT%22%0A++++++++++++wget+-q+https%3A%2F%2Fdl.google.com%2Fandroid%2Frepository%2Fcommandlinetools-linux-11076708_latest.zip+-O+cmdline-tools.zip%0A++++++++++++mkdir+-p+cmdline-tools%2Flatest%0A++++++++++++unzip+-q+cmdline-tools.zip%0A++++++++++++mv+cmdline-tools%2F%2A+cmdline-tools%2Flatest%2F+2%3E%2Fdev%2Fnull+%7C%7C+true%0A++++++++++++rm+cmdline-tools.zip%0A++++++++++++SDKMAN%3D%22%24ANDROID_SDK_ROOT%2Fcmdline-tools%2Flatest%2Fbin%2Fsdkmanager%22%0A++++++++++++chmod+%2Bx+%22%24SDKMAN%22%0A++++++++++fi%0A++++++++++yes+%7C+%22%24SDKMAN%22+--licenses+%3E+%2Fdev%2Fnull+%7C%7C+true%0A++++++++++%22%24SDKMAN%22+--sdk_root%3D%22%24ANDROID_SDK_ROOT%22+%22platforms%3Bandroid-34%22+%22build-tools%3B34.0.0%22+%22platform-tools%22%0A%0A++++++-+name%3A+Grant+execute+permission+for+gradlew%0A++++++++run%3A+chmod+%2Bx+gradlew%0A++++++++working-directory%3A+android%0A%0A++++++-+name%3A+Download+Gradle+Wrapper+jar%0A++++++++run%3A+%7C%0A++++++++++mkdir+-p+gradle%2Fwrapper%0A++++++++++curl+-fsSL+--retry+3+-o+gradle%2Fwrapper%2Fgradle-wrapper.jar+https%3A%2F%2Fraw.githubusercontent.com%2Fgradle%2Fgradle%2Fv8.5.0%2Fgradle%2Fwrapper%2Fgradle-wrapper.jar%0A++++++++working-directory%3A+android%0A%0A++++++-+name%3A+Create+local.properties%0A++++++++run%3A+%7C%0A++++++++++echo+%22sdk.dir%3D%24ANDROID_SDK_ROOT%22+%3E+local.properties%0A++++++++working-directory%3A+android%0A%0A++++++-+name%3A+Build+release+APK%0A++++++++run%3A+.%2Fgradlew+assembleRelease+--no-daemon+--stacktrace%0A++++++++working-directory%3A+android%0A%0A++++++-+name%3A+Find+APK%0A++++++++id%3A+apk%0A++++++++run%3A+%7C%0A++++++++++APK_PATH%3D%24%28find+app%2Fbuild%2Foutputs%2Fapk%2Frelease+-name+%22%2A.apk%22+-type+f+%7C+head+-1%29%0A++++++++++echo+%22path%3D%24APK_PATH%22+%3E%3E+%22%24GITHUB_OUTPUT%22%0A++++++++working-directory%3A+android%0A%0A++++++-+name%3A+Upload+APK+artifact%0A++++++++uses%3A+actions%2Fupload-artifact%40v4%0A++++++++with%3A%0A++++++++++name%3A+shortly-release-apk%0A++++++++++path%3A+android%2F%24%7B%7B+steps.apk.outputs.path+%7D%7D%0A++++++++++if-no-files-found%3A+error%0A)**

Once you commit that page, GitHub Actions will run automatically and upload a
downloadable **shortly-release-apk** artifact on the **Actions** tab. Every
subsequent push will rebuild the APK.

## Project structure

```
android/    Native Kotlin + Jetpack Compose + Material 3 Android app
api/        PHP 8 REST API with flock()-based JSON-file database
.setup/     The GitHub Actions workflow YAML (source for the link above)
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
Output APK: `android/app/build/outputs/apk/release/app-release-unsigned.apk`.
Requires JDK 17 and Android SDK 34 (Build-Tools 34.0.0). Android Studio works
out of the box.

## Deploying the PHP API

Upload the entire `api/` directory to a PHP 8+ host and make `api/data/`,
`api/uploads/videos/`, and `api/uploads/avatars/` writable by the web server.
See `BUILD.md` for full instructions.
