plugins {
    alias(libs.plugins.fugitive.android.library)
    alias(libs.plugins.fugitive.android.compose)
}

android {
    namespace = "app.fugitive.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
