plugins {
    alias(libs.plugins.fugitive.android.feature)
}

android {
    namespace = "app.fugitive.feature.lobby"
}

dependencies {
    implementation(projects.core.map)
}
