plugins {
    alias(libs.plugins.fugitive.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.fugitive.backend.firebase"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
}
