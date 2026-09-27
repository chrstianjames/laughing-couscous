# BUILD.md — Building the Shortly APK

## Continuous integration

The workflow is `.github/workflows/android-build.yml`. It runs on every push and
pull request to `main`/`master` (and on `arena/**` branches) and can be started
manually (*Actions → Build Android APK → Run workflow*).

What it does:

1. `actions/checkout`
2. `actions/setup-java` — Temurin JDK 17 (required by AGP 8.2)
3. `gradle/actions/setup-gradle` — caches `~/.gradle` between runs
4. uses the Android SDK already on the `ubuntu-24.04` runner
   (`platforms;android-34`, `build-tools;34.0.0`) and writes
   `android/local.properties` (`sdk.dir=…`). It does **not** call
   `android-actions/setup-android` (that action requests the removed `tools`
   package and fails the job)
5. prepares signing (see below)
6. `./gradlew assembleRelease --stacktrace` inside `android/`
7. locates the APK, fails loudly if there is none, writes path/size to the job
   summary
8. uploads it as the **`shortly-release-apk`** artifact (30-day retention)

Download the artifact from the run page:
<https://github.com/chrstianjames/laughing-couscous/actions>

### Signing

`android/app/build.gradle.kts` looks for these four values, either as Gradle
properties (`-PSHORTLY_STORE_FILE=…`) or as environment variables:

| Name | Meaning |
| --- | --- |
| `SHORTLY_STORE_FILE` | path to the keystore |
| `SHORTLY_STORE_PASSWORD` | keystore password |
| `SHORTLY_KEY_ALIAS` | key alias |
| `SHORTLY_KEY_PASSWORD` | key password |

In CI they are read from the repository secrets of the same name. When the
secrets are absent the workflow generates a temporary PKCS12 keystore with
`keytool`, so `assembleRelease` still yields an **installable** APK instead of
`app-release-unsigned.apk`. Add the secrets when you are ready to publish to
Play:

```bash
base64 -w0 your-release-key.jks   # -> SHORTLY_KEYSTORE_BASE64
```

## Local build

Requirements: JDK 17, Android SDK 34 with Build-Tools 34.0.0.

```bash
cd android
./gradlew assembleRelease
```

Output: `android/app/build/outputs/apk/release/app-release.apk`

The Gradle wrapper (scripts **and** `gradle/wrapper/gradle-wrapper.jar`) is
committed, so `./gradlew` works on a fresh clone — no separate Gradle install
needed. Android Studio opens the `android/` folder directly and needs no extra
configuration; it writes `local.properties` with your SDK path automatically.

For a command-line build outside Android Studio, point Gradle at the SDK once:

```bash
echo "sdk.dir=$ANDROID_HOME" > android/local.properties
```

Install it on a device:

```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

## CI history / gotchas

Fixed in this repository — worth knowing if a workflow is edited again:

* **`Warning: Failed to find package 'tools'`** — `android-actions/setup-android`
  defaults to `packages: tools platform-tools`, but Google removed the legacy
  `tools` package from the SDK repository, so `sdkmanager` exits 1 and fails the
  job. The action is no longer used; the runner image already contains the SDK
  and the workflow installs only what AGP needs.
* **`Unexpected input(s) 'build-root-directory'`** — that input belongs to
  `gradle/actions/gradle-build-action`, not `setup-gradle`; it was ignored, so
  the working directory is set per step instead.
* **`./gradlew` was a stub** — the committed `gradlew`/`gradlew.bat` are the real
  Gradle 8.5 scripts and `gradle-wrapper.jar` is checked in, so no wrapper jar
  has to be downloaded at build time.

## API deployment

Upload the `api/` folder to any PHP 8+ host and make these directories writable:

- `api/data/`
- `api/uploads/videos/`
- `api/uploads/avatars/`

`.htaccess` rules already protect the JSON database files and disable PHP
execution inside the upload directories.

The app talks to `https://app.chinai.uk/api/` — change it in
`android/app/src/main/java/com/shortly/app/util/Constants.kt` and rebuild.
