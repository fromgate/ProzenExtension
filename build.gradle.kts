import java.io.File

plugins {
    kotlin("multiplatform") version "2.0.20"
    id("base")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled = true
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
            }
            kotlin.srcDir("src/jsMain/kotlin")
            resources.srcDir("src/jsMain/resources")
        }
        val jsTest by getting {
            kotlin.srcDir("src/jsTest/kotlin")
        }
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val changelogFile = file("CHANGELOG.md")

fun extractVersionFromChangelog(file: File): String {
    val versionRegex = Regex("""^### (\d+\.\d+\.\d+)""")
    for (line in file.readLines()) {
        val matchResult = versionRegex.find(line)
        if (matchResult != null) {
            return matchResult.groupValues[1]
        }
    }
    throw GradleException("Version not found in CHANGELOG.md")
}

val version = extractVersionFromChangelog(changelogFile)

tasks {
    val updateManifests by creating {
        group = "build"
        description = "Updates manifest.json with the version from CHANGELOG.md"

        val chromeManifestFile = file("src/jsMain/resources/chrome/manifest.json")
        val firefoxManifestFile = file("src/jsMain/resources/firefox/manifest.json")
        val edgeManifestFile = file("src/jsMain/resources/edge/manifest.json")

        doLast {
            listOf(chromeManifestFile, firefoxManifestFile, edgeManifestFile).forEach { manifestFile ->
                val manifestContent = manifestFile.readText()
                val updatedContent = manifestContent.replace(
                    Regex("\"version\": \"\\d+\\.\\d+\\.\\d+\""),
                    "\"version\": \"$version\""
                )
                manifestFile.writeText(updatedContent)
            }
            println("Manifests updated to version $version")
        }
    }

    val processChromeResources by creating(Copy::class) {
        from("src/jsMain/resources/chrome") {
            include("**/manifest.json")
            into("chrome")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        into(layout.buildDirectory.dir("processedResources/jsMain/chrome").get().asFile)
    }

    val processFirefoxResources by creating(Copy::class) {
        from("src/jsMain/resources/firefox") {
            include("**/manifest.json")
            into("firefox")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        into(layout.buildDirectory.dir("processedResources/jsMain/firefox").get().asFile)
    }

    val processEdgeResources by creating(Copy::class) {
        from("src/jsMain/resources/edge") {
            include("**/manifest.json")
            into("edge")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        into(layout.buildDirectory.dir("processedResources/jsMain/edge").get().asFile)
    }

    // Main task to process all resources
    val processAllResources by creating {
        dependsOn(processChromeResources, processFirefoxResources, processEdgeResources)
    }

    // Assemble Chrome extension
    val assembleChrome by creating(Copy::class) {
        dependsOn(updateManifests, processAllResources)
        group = "build"
        description = "Assemble Chrome extension"
        from(layout.buildDirectory.dir("processedResources/jsMain/chrome").get().asFile)
        into(layout.buildDirectory.dir("dist/chrome").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val zipChrome by creating(Zip::class) {
        dependsOn(assembleChrome)
        from(layout.buildDirectory.dir("dist/chrome").get().asFile)
        archiveFileName.set("prozen-chrome.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    // Assemble Firefox extension
    val assembleFirefox by creating(Copy::class) {
        dependsOn(updateManifests, processAllResources)
        group = "build"
        description = "Assemble Firefox extension"
        from(layout.buildDirectory.dir("processedResources/jsMain/firefox").get().asFile)
        into(layout.buildDirectory.dir("dist/firefox").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val zipFirefox by creating(Zip::class) {
        dependsOn(assembleFirefox)
        from(layout.buildDirectory.dir("dist/firefox").get().asFile)
        archiveFileName.set("prozen-firefox.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    // Assemble Edge extension
    val assembleEdge by creating(Copy::class) {
        dependsOn(updateManifests, processAllResources)
        group = "build"
        description = "Assemble Edge extension"
        from(layout.buildDirectory.dir("processedResources/jsMain/edge").get().asFile)
        into(layout.buildDirectory.dir("dist/edge").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val zipEdge by creating(Zip::class) {
        dependsOn(assembleEdge)
        from(layout.buildDirectory.dir("dist/edge").get().asFile)
        archiveFileName.set("prozen-edge.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    assemble {
        dependsOn(zipChrome, zipFirefox, zipEdge)
    }
}
