package app.fugitive.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Configuration shared by every Android module. Application and library
 * extensions are configured separately (rather than through CommonExtension)
 * so the build logic stays stable across Android Gradle Plugin API changes.
 */
internal fun Project.configureAndroidApplication(ext: ApplicationExtension) = with(ext) {
    compileSdk = libs.int("compileSdk")
    defaultConfig {
        minSdk = libs.int("minSdk")
        targetSdk = libs.int("targetSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
            "/META-INF/*.kotlin_module",
            "DebugProbesKt.bin",
        )
    }
    configureKotlinCompiler()
}

internal fun Project.configureAndroidLibrary(ext: LibraryExtension) = with(ext) {
    compileSdk = libs.int("compileSdk")
    defaultConfig {
        minSdk = libs.int("minSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    configureKotlinCompiler()
    dependencies {
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
    }
}
