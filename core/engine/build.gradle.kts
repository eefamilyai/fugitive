plugins {
    alias(libs.plugins.fugitive.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
}
