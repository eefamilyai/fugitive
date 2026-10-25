plugins {
    alias(libs.plugins.fugitive.android.library)
}

android {
    namespace = "app.fugitive.core.location"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
}
