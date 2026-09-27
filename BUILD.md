# Setting up GitHub Actions APK Build

Because the Arena automation token used to push code doesn't have the `workflows` permission
required to create/modify files under `.github/workflows/`, the workflow YAML is shipped at
`.setup/android-build.yml` instead.

## One-step setup (via browser)

After pushing this repository to GitHub, enable automatic release-APK builds by copying the
workflow into place:

1. Open this repository on GitHub.
2. Click **Add file → Create new file**.
3. Set the filename to: `.github/workflows/android-build.yml`
4. Paste the entire contents of `.setup/android-build.yml` (already in this repo) into the editor.
5. Commit directly to your branch.

GitHub Actions will automatically run on the next push and produce a signed release APK as a
downloadable artifact under the **Actions** tab.

## Local build (requires Android Studio / JDK 17 + Android SDK 34)

```bash
cd android
./gradlew assembleRelease
# Output APK: android/app/build/outputs/apk/release/app-release-unsigned.apk
```

## What the workflow does

1. Checks out the code
2. Sets up JDK 17 (Temurin)
3. Caches Gradle dependencies
4. Installs Android SDK 34, Build-Tools 34.0.0, Platform-Tools
5. Downloads the Gradle wrapper jar
6. Runs `./gradlew assembleRelease`
7. Uploads the resulting APK as a GitHub Actions artifact you can download

## API deployment

Upload the contents of the `api/` directory to any PHP 8+ web host:

```
api/config.php         <- core config / JSON DB helpers
api/index.php          <- router + all endpoints
api/data/              <- JSON database (must be writable by the web server)
api/uploads/           <- uploaded videos/avatars (created automatically)
api/uploads/.htaccess  <- blocks PHP execution in uploads
api/.htaccess          <- blocks direct access to data/
```

Make sure `data/`, `uploads/videos/`, and `uploads/avatars/` are writable (chmod 775 or 777
depending on host).
