plugins {
    kotlin("multiplatform") version "2.0.20" // apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
}

kotlin {
    js(IR) {
        browser {
        }
        binaries.executable()
    }
}

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

tasks.named("build") {
    dependsOn(":prozen-firefox:packageExtension")
}