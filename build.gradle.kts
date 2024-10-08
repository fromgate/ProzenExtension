plugins {
    kotlin("multiplatform") version "2.0.20" // apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
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

tasks.register("buildVersion") {
    group = "build"
    description = "Build the project and tag the current version"
    dependsOn("build", ":prozen:tagVersion")
}