
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}


dependencies {
    implementation(libs.kotlinx.serialization.json)
}
