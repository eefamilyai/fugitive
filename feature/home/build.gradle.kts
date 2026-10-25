plugins {
    alias(libs.plugins.fugitive.android.feature)
}

android {
    namespace = "app.fugitive.feature.home"
}

dependencies {
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.play.services)
}
