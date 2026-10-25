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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
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
