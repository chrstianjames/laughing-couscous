# Applying the fixed workflow to `main`

The corrected workflow is committed here as
[`.setup/android-build.yml`](android-build.yml), because GitHub refuses to let the
Arena coding agent write to `.github/workflows/**`:

```
remote: refusing to allow a GitHub App to create or update workflow
        `.github/workflows/android-build.yml` without `workflows` permission
```

The copy currently on `main` still fails in **Setup Android SDK** with
`Warning: Failed to find package 'tools'`. Pick **one** of the three options
below - they all end with the same result.

---

## Option 1 - grant the permission (recommended, fully automatic)

1. Open <https://github.com/settings/installations>
2. Find **Arena AI Coding Agent** -> **Configure**
3. Under *Repository permissions* set **Workflows** -> **Read and write** -> *Save*
4. Tell the agent "done" - it pushes the file, re-runs the build, watches it go
   green and merges PR #2. Nothing else for you to do.

## Option 2 - one click, content pre-filled

[Open the workflow editor with the fixed YAML already filled in](https://github.com/chrstianjames/laughing-couscous/new/main?filename=.github%2Fworkflows%2Fandroid-build.yml&value=name%3A%20Build%20Android%20APK%0A%0Aon%3A%0A%20%20push%3A%0A%20%20%20%20branches%3A%20%5Bmain%2C%20master%5D%0A%20%20pull_request%3A%0A%20%20%20%20branches%3A%20%5Bmain%2C%20master%5D%0A%20%20workflow_dispatch%3A%0A%0Apermissions%3A%0A%20%20contents%3A%20read%0A%0Aconcurrency%3A%0A%20%20group%3A%20%24%7B%7B%20github.workflow%20%7D%7D-%24%7B%7B%20github.ref%20%7D%7D%0A%20%20cancel-in-progress%3A%20true%0A%0Ajobs%3A%0A%20%20build%3A%0A%20%20%20%20name%3A%20Build%20Release%20APK%0A%20%20%20%20runs-on%3A%20ubuntu-latest%0A%20%20%20%20timeout-minutes%3A%2045%0A%20%20%20%20steps%3A%0A%20%20%20%20%20%20-%20uses%3A%20actions%2Fcheckout%40v4%0A%20%20%20%20%20%20-%20uses%3A%20actions%2Fsetup-java%40v4%0A%20%20%20%20%20%20%20%20with%3A%0A%20%20%20%20%20%20%20%20%20%20java-version%3A%20%2717%27%0A%20%20%20%20%20%20%20%20%20%20distribution%3A%20%27temurin%27%0A%20%20%20%20%20%20-%20uses%3A%20gradle%2Factions%2Fsetup-gradle%40v4%0A%0A%20%20%20%20%20%20%23%20android-actions%2Fsetup-android%20is%20deliberately%20NOT%20used%3A%20its%20default%0A%20%20%20%20%20%20%23%20%22packages%3A%20tools%20platform-tools%22%20asks%20sdkmanager%20for%20the%20legacy%20%22tools%22%0A%20%20%20%20%20%20%23%20package%20that%20Google%20removed%2C%20which%20fails%20the%20job%20with%0A%20%20%20%20%20%20%23%20%22Warning%3A%20Failed%20to%20find%20package%20%27tools%27%22%20%2B%20exit%20code%201.%0A%20%20%20%20%20%20-%20name%3A%20Install%20Android%20SDK%20packages%0A%20%20%20%20%20%20%20%20run%3A%20%7C%0A%20%20%20%20%20%20%20%20%20%20set%20-euo%20pipefail%0A%20%20%20%20%20%20%20%20%20%20SDK%3D%22%24%7BANDROID_HOME%3A-%2Fusr%2Flocal%2Flib%2Fandroid%2Fsdk%7D%22%0A%20%20%20%20%20%20%20%20%20%20SM%3D%22%24%28ls%20-1%20%22%24SDK%22%2Fcmdline-tools%2F%2A%2Fbin%2Fsdkmanager%202%3E%2Fdev%2Fnull%20%7C%20sort%20-V%20%7C%20tail%20-1%20%7C%7C%20true%29%22%0A%20%20%20%20%20%20%20%20%20%20if%20%5B%20-z%20%22%24SM%22%20%5D%3B%20then%0A%20%20%20%20%20%20%20%20%20%20%20%20curl%20-fsSL%20--retry%203%20-o%20%2Ftmp%2Fclt.zip%20https%3A%2F%2Fdl.google.com%2Fandroid%2Frepository%2Fcommandlinetools-linux-11076708_latest.zip%0A%20%20%20%20%20%20%20%20%20%20%20%20mkdir%20-p%20%22%24SDK%2Fcmdline-tools%22%0A%20%20%20%20%20%20%20%20%20%20%20%20unzip%20-q%20%2Ftmp%2Fclt.zip%20-d%20%22%24SDK%2Fcmdline-tools%22%0A%20%20%20%20%20%20%20%20%20%20%20%20mv%20%22%24SDK%2Fcmdline-tools%2Fcmdline-tools%22%20%22%24SDK%2Fcmdline-tools%2Flatest%22%0A%20%20%20%20%20%20%20%20%20%20%20%20SM%3D%22%24SDK%2Fcmdline-tools%2Flatest%2Fbin%2Fsdkmanager%22%0A%20%20%20%20%20%20%20%20%20%20fi%0A%20%20%20%20%20%20%20%20%20%20yes%20%7C%20%22%24SM%22%20--sdk_root%3D%22%24SDK%22%20--licenses%20%3E%2Fdev%2Fnull%202%3E%261%20%7C%7C%20true%0A%20%20%20%20%20%20%20%20%20%20%22%24SM%22%20--sdk_root%3D%22%24SDK%22%20platform-tools%20%22platforms%3Bandroid-34%22%20%22build-tools%3B34.0.0%22%0A%20%20%20%20%20%20%20%20%20%20echo%20%22ANDROID_HOME%3D%24SDK%22%20%3E%3E%20%22%24GITHUB_ENV%22%0A%20%20%20%20%20%20%20%20%20%20echo%20%22sdk.dir%3D%24SDK%22%20%3E%20android%2Flocal.properties%0A%0A%20%20%20%20%20%20%23%20Signs%20with%20SHORTLY_%2A%20secrets%20when%20present%2C%20otherwise%20a%20throwaway%20CI%20key%2C%0A%20%20%20%20%20%20%23%20so%20the%20artifact%20is%20installable%20instead%20of%20app-release-unsigned.apk.%0A%20%20%20%20%20%20-%20name%3A%20Prepare%20signing%20key%0A%20%20%20%20%20%20%20%20env%3A%0A%20%20%20%20%20%20%20%20%20%20KS%3A%20%24%7B%7B%20secrets.SHORTLY_KEYSTORE_BASE64%20%7D%7D%0A%20%20%20%20%20%20%20%20%20%20SP%3A%20%24%7B%7B%20secrets.SHORTLY_STORE_PASSWORD%20%7D%7D%0A%20%20%20%20%20%20%20%20%20%20KA%3A%20%24%7B%7B%20secrets.SHORTLY_KEY_ALIAS%20%7D%7D%0A%20%20%20%20%20%20%20%20%20%20KP%3A%20%24%7B%7B%20secrets.SHORTLY_KEY_PASSWORD%20%7D%7D%0A%20%20%20%20%20%20%20%20run%3A%20%7C%0A%20%20%20%20%20%20%20%20%20%20set%20-euo%20pipefail%0A%20%20%20%20%20%20%20%20%20%20F%3D%22%24RUNNER_TEMP%2Frelease.keystore%22%0A%20%20%20%20%20%20%20%20%20%20if%20%5B%20-n%20%22%24%7BKS%3A-%7D%22%20%5D%3B%20then%0A%20%20%20%20%20%20%20%20%20%20%20%20printf%20%27%25s%27%20%22%24KS%22%20%7C%20base64%20-d%20%3E%20%22%24F%22%0A%20%20%20%20%20%20%20%20%20%20%20%20P%3D%22%24%7BSP%3A%3FSHORTLY_STORE_PASSWORD%20required%7D%22%3B%20A%3D%22%24%7BKA%3A%3FSHORTLY_KEY_ALIAS%20required%7D%22%3B%20K%3D%22%24%7BKP%3A%3FSHORTLY_KEY_PASSWORD%20required%7D%22%0A%20%20%20%20%20%20%20%20%20%20else%0A%20%20%20%20%20%20%20%20%20%20%20%20P%3Dshortly-ci%3B%20A%3Dshortly%3B%20K%3Dshortly-ci%0A%20%20%20%20%20%20%20%20%20%20%20%20keytool%20-genkeypair%20-keystore%20%22%24F%22%20-storetype%20PKCS12%20-storepass%20%22%24P%22%20-keypass%20%22%24K%22%20-alias%20%22%24A%22%20-keyalg%20RSA%20-keysize%202048%20-validity%2010000%20-dname%20%22CN%3DShortly%20CI%2C%20O%3DShortly%2C%20C%3DUS%22%0A%20%20%20%20%20%20%20%20%20%20fi%0A%20%20%20%20%20%20%20%20%20%20%7B%20echo%20%22SHORTLY_STORE_FILE%3D%24F%22%3B%20echo%20%22SHORTLY_STORE_PASSWORD%3D%24P%22%3B%20echo%20%22SHORTLY_KEY_ALIAS%3D%24A%22%3B%20echo%20%22SHORTLY_KEY_PASSWORD%3D%24K%22%3B%20%7D%20%3E%3E%20%22%24GITHUB_ENV%22%0A%0A%20%20%20%20%20%20-%20name%3A%20Build%20release%20APK%0A%20%20%20%20%20%20%20%20working-directory%3A%20android%0A%20%20%20%20%20%20%20%20run%3A%20.%2Fgradlew%20assembleRelease%20--no-daemon%20--stacktrace%0A%0A%20%20%20%20%20%20-%20name%3A%20Locate%20APK%0A%20%20%20%20%20%20%20%20id%3A%20apk%0A%20%20%20%20%20%20%20%20working-directory%3A%20android%0A%20%20%20%20%20%20%20%20run%3A%20%7C%0A%20%20%20%20%20%20%20%20%20%20set%20-euo%20pipefail%0A%20%20%20%20%20%20%20%20%20%20P%3D%22%24%28find%20app%2Fbuild%2Foutputs%2Fapk%2Frelease%20-name%20%27%2A.apk%27%20%7C%20head%20-1%29%22%0A%20%20%20%20%20%20%20%20%20%20%5B%20-n%20%22%24P%22%20%5D%20%7C%7C%20%7B%20echo%20%22%3A%3Aerror%3A%3ANo%20APK%20produced%22%3B%20exit%201%3B%20%7D%0A%20%20%20%20%20%20%20%20%20%20echo%20%22path%3D%24P%22%20%3E%3E%20%22%24GITHUB_OUTPUT%22%0A%20%20%20%20%20%20%20%20%20%20%7B%20echo%20%22%23%23%23%20Shortly%20release%20APK%22%3B%20echo%20%22-%20%5C%60android%2F%24P%5C%60%20%28%24%28du%20-h%20%22%24P%22%20%7C%20cut%20-f1%29%29%22%3B%20%7D%20%3E%3E%20%22%24GITHUB_STEP_SUMMARY%22%0A%0A%20%20%20%20%20%20-%20uses%3A%20actions%2Fupload-artifact%40v4%0A%20%20%20%20%20%20%20%20with%3A%0A%20%20%20%20%20%20%20%20%20%20name%3A%20shortly-release-apk%0A%20%20%20%20%20%20%20%20%20%20path%3A%20android%2F%24%7B%7B%20steps.apk.outputs.path%20%7D%7D%0A%20%20%20%20%20%20%20%20%20%20if-no-files-found%3A%20error%0A%20%20%20%20%20%20%20%20%20%20retention-days%3A%2030%0A)

Scroll down and press **Commit changes...** -> *Commit directly to the `main`
branch*. That is the whole job - the build starts immediately. (If GitHub
complains that the file already exists, use Option 3.)

## Option 3 - copy & paste

1. Open the fixed YAML and press **Copy raw file**:
   <https://github.com/chrstianjames/laughing-couscous/blob/arena/01a0e194-laughing-couscous/.setup/android-build.yml>
2. Open the workflow on `main` in the editor:
   <https://github.com/chrstianjames/laughing-couscous/edit/main/.github/workflows/android-build.yml>
3. `Ctrl+A` -> `Delete` -> paste -> **Commit changes...** directly to `main`.

---

## Afterwards

The commit to `main` starts a build immediately. It ends with the
**`shortly-release-apk`** artifact (a signed, installable APK) on the run page:
<https://github.com/chrstianjames/laughing-couscous/actions>

PR #2 (the Kotlin/Gradle fixes) can be merged before or after - its own check
only turns green once the fixed workflow is on `main`, because pull-request runs
use the workflow from the merge with `main`.
