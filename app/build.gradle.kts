import java.util.Properties

plugins {
    alias(libs.plugins.fugitive.android.application)
    alias(libs.plugins.fugitive.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ── Optional Firebase configuration ─────────────────────────────────────
// Online multiplayer needs app/google-services.json (see docs/FIREBASE_SETUP.md).
// Without it the app still builds and runs; online play is disabled and
// practice mode works offline.
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) apply(plugin = "com.google.gms.google-services")

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// Debug builds can talk to the Firebase Local Emulator Suite instead of a real project:
// add `fugitive.firebaseEmulatorHost=10.0.2.2` to local.properties.
val emulatorHost = localProps.getProperty("fugitive.firebaseEmulatorHost", "")

val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.fugitive"

    defaultConfig {
        applicationId = "com.jaryl.fugitive"
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasGoogleServices.toString())
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.engine)
    implementation(projects.core.data)
    implementation(projects.core.location)
    implementation(projects.core.map)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.backend.local)
    implementation(projects.backend.firebase)
    implementation(projects.feature.home)
    implementation(projects.feature.lobby)
    implementation(projects.feature.game)
    implementation(projects.feature.results)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
}
