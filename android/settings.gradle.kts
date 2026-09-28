pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Shortly"
include(":shared")   // Compose Multiplatform code shared by Android and Web
include(":app")      // Android application (thin shell around :shared)
include(":webApp")   // Kotlin/Wasm web application (static files)
