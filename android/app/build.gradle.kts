plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

/**
 * Release signing is optional.
 *
 * Values can be supplied either as Gradle properties (-PSHORTLY_STORE_FILE=...) or as plain
 * environment variables, so CI can sign the APK without any file being committed to the repo.
 * When nothing is provided the release build falls back to the auto-generated debug key so the
 * produced APK is still installable for testing.
 */
fun signingProp(name: String): String? =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProp("SHORTLY_STORE_FILE")
val releaseStorePassword = signingProp("SHORTLY_STORE_PASSWORD")
val releaseKeyAlias = signingProp("SHORTLY_KEY_ALIAS")
val releaseKeyPassword = signingProp("SHORTLY_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).none { it.isNullOrBlank() }

android {
    namespace = "com.shortly.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shortly.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Fall back to the debug key so `assembleRelease` always yields an installable APK.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    // All UI, data and business logic lives in :shared (Compose Multiplatform).
    implementation(project(":shared"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.preview)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)

    debugImplementation(compose.uiTooling)
}
