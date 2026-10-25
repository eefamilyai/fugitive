plugins {
    alias(libs.plugins.fugitive.android.feature)
}

android {
    namespace = "app.fugitive.feature.results"
}

dependencies {
    implementation(projects.core.map)
}
