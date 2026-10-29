import app.fugitive.buildlogic.lib
import app.fugitive.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Pure Kotlin module compiled for the JVM (Android, unit tests) and JavaScript (the web client).
 * Code in `commonMain` is shared by both, so game rules exist exactly once.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
            js(IR) {
                browser()
                binaries.library()
                useEsModules()
                generateTypeScriptDefinitions()
            }
            compilerOptions {
                freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn", "-Xexpect-actual-classes")
                optIn.add("kotlin.js.ExperimentalJsExport")
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(kotlin("test"))
            }
            sourceSets.getByName("jvmTest").dependencies {
                implementation(libs.lib("junit"))
                implementation(libs.lib("truth"))
            }
        }
    }
}
