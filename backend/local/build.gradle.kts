plugins {
    alias(libs.plugins.fugitive.android.library)
}

android {
    namespace = "app.fugitive.backend.local"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    testImplementation(libs.turbine)
}
