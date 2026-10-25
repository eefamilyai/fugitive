import app.fugitive.buildlogic.lib
import app.fugitive.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * A feature module: an Android library with Compose, wired to the shared core
 * modules. New screens live in new feature modules that only apply this plugin.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("fugitive.android.library")
        pluginManager.apply("fugitive.android.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:engine"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))

            add("implementation", libs.lib("androidx-core-ktx"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
            add("implementation", platform(libs.lib("koin-bom")))
            add("implementation", libs.lib("koin-androidx-compose"))
        }
    }
}
