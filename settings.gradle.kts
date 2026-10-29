pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Kotlin/JS adds Node.js / Yarn download repositories at project level; settings repositories win.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Toolchains downloaded by the Kotlin/JS build of core:engine (web client).
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Fugitive"

// ── App ─────────────────────────────────────────────────────────────
include(":app")

// ── Core: pure Kotlin (JVM) — no Android dependencies, fully unit-testable
include(":core:model")      // Serializable domain types shared by every layer
include(":core:engine")     // Game rules: modes, settings schema, zone, visibility, referee

// ── Core: Android
include(":core:data")         // Backend contract, repositories, preferences, session orchestration
include(":core:location")     // Location sources + foreground tracking service
include(":core:map")          // MapLibre wrapper, layer API, style registry
include(":core:designsystem") // Theme, tokens, reusable components
include(":core:ui")           // Domain-aware UI pieces (avatars, role chips, settings form renderer)

// ── Backends: pluggable implementations of core:data's GameBackend
include(":backend:local")     // In-memory backend for offline practice with bots
include(":backend:firebase")  // Firebase Realtime Database backend for online play

// ── Features
include(":feature:home")
include(":feature:lobby")
include(":feature:game")
include(":feature:results")
include(":feature:settings")
