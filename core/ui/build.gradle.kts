plugins {
    alias(libs.plugins.fugitive.android.library)
    alias(libs.plugins.fugitive.android.compose)
}

android {
    namespace = "app.fugitive.core.ui"
}

dependencies {
    api(projects.core.model)
    api(projects.core.engine)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.zxing.core)
    implementation(libs.androidx.activity.compose)
}
