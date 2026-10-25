plugins {
    alias(libs.plugins.fugitive.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.fugitive.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.engine)
    api(projects.core.location)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    testImplementation(libs.turbine)
}
