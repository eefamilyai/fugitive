plugins {
    alias(libs.plugins.fugitive.android.library)
    alias(libs.plugins.fugitive.android.compose)
}

android {
    namespace = "app.fugitive.core.map"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.engine)
    api(libs.maplibre.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
