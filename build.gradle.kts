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


/*


fun extractVersionFromChangelog(file: File): String {


    throw GradleException("Version not found in CHANGELOG.md")
}

val version = extractVersionFromChangelog(changelogFile)
 */

tasks.named("build") {
    //dependsOn(":prozen:packageExtension")
    dependsOn(":prozen-firefox:packageExtension")
}