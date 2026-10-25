plugins {
    alias(libs.plugins.fugitive.android.feature)
}

android {
    namespace = "app.fugitive.feature.game"
}

dependencies {
    implementation(projects.core.map)
    implementation(projects.core.location)
}
