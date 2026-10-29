plugins {
    alias(libs.plugins.fugitive.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    js(IR) {
        // Name of the generated ES module the web client imports.
        outputModuleName.set("fugitive-engine")
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
        }
    }
}

/**
 * Builds the JavaScript engine and copies it into the web client (web/src/engine).
 * Run after changing any rules: ./gradlew :core:engine:syncWebEngine
 */
val syncWebEngine by tasks.registering(Sync::class) {
    group = "web"
    description = "Compiles the shared game engine to JavaScript and copies it into web/src/engine."
    dependsOn("jsBrowserProductionLibraryDistribution")
    from(layout.buildDirectory.dir("dist/js/productionLibrary"))
    into(rootProject.layout.projectDirectory.dir("web/src/engine"))
}
