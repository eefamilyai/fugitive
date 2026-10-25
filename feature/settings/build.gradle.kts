plugins {
    alias(libs.plugins.fugitive.android.feature)
}

android {
    namespace = "app.fugitive.feature.settings"
}

dependencies {
    implementation(projects.core.map)
    implementation(projects.core.location)
}
