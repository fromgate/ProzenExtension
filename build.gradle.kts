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
            resources.srcDir("src/jsMain/resources").apply {
                exclude("**/manifest.json")
                exclude("common/**")
            }
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

    val processCommonResources by creating(Copy::class) {
        from("src/jsMain/resources/common")
        into(layout.buildDirectory.dir("processedResources/common").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val copyJsFiles by creating(Copy::class) {
        from("src/jsMain/js")
        into(layout.buildDirectory.dir("processedResources/js").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val jsProcessResources = named<Copy>("jsProcessResources") {
        dependsOn(copyJsFiles)
    }

    named<Jar>("jsJar") {
        dependsOn(copyJsFiles)
    }

    named<Copy>("jsBrowserDistribution") {
        dependsOn(copyJsFiles)
    }

    val processChromeResources by creating(Copy::class) {
        dependsOn(processCommonResources, updateManifests, jsProcessResources)
        from("src/jsMain/resources/chrome")
        from(layout.buildDirectory.dir("processedResources/common").get().asFile)
        into(layout.buildDirectory.dir("processedResources/chrome").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        into("js") {
            from(layout.buildDirectory.dir("processedResources/js").get().asFile)
        }
    }

    val processFirefoxResources by creating(Copy::class) {
        dependsOn(processCommonResources, updateManifests, jsProcessResources)
        from("src/jsMain/resources/firefox")
        from(layout.buildDirectory.dir("processedResources/common").get().asFile)
        into(layout.buildDirectory.dir("processedResources/firefox").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        into("js") {
            from(layout.buildDirectory.dir("processedResources/js").get().asFile)
        }
    }

    val processEdgeResources by creating(Copy::class) {
        dependsOn(processCommonResources, updateManifests, jsProcessResources)
        from("src/jsMain/resources/edge")
        from(layout.buildDirectory.dir("processedResources/common").get().asFile)
        into(layout.buildDirectory.dir("processedResources/edge").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        into("js") {
            from(layout.buildDirectory.dir("processedResources/js").get().asFile)
        }
    }

    val assembleChrome by creating(Copy::class) {
        dependsOn(processChromeResources)
        group = "build"
        description = "Assemble Chrome extension"
        from(layout.buildDirectory.dir("processedResources/chrome").get().asFile)
        into(layout.buildDirectory.dir("dist/chrome").get().asFile)
    }

    val zipChrome by creating(Zip::class) {
        dependsOn(assembleChrome)
        from(layout.buildDirectory.dir("dist/chrome").get().asFile)
        archiveFileName.set("prozen-chrome.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions").get().asFile)
    }

    val assembleFirefox by creating(Copy::class) {
        dependsOn(processFirefoxResources)
        group = "build"
        description = "Assemble Firefox extension"
        from(layout.buildDirectory.dir("processedResources/firefox").get().asFile)
        into(layout.buildDirectory.dir("dist/firefox").get().asFile)
    }

    val zipFirefox by creating(Zip::class) {
        dependsOn(assembleFirefox)
        from(layout.buildDirectory.dir("dist/firefox").get().asFile)
        archiveFileName.set("prozen-firefox.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions").get().asFile)
    }

    val assembleEdge by creating(Copy::class) {
        dependsOn(processEdgeResources)
        group = "build"
        description = "Assemble Edge extension"
        from(layout.buildDirectory.dir("processedResources/edge").get().asFile)
        into(layout.buildDirectory.dir("dist/edge").get().asFile)
    }

    val zipEdge by creating(Zip::class) {
        dependsOn(assembleEdge)
        from(layout.buildDirectory.dir("dist/edge").get().asFile)
        archiveFileName.set("prozen-edge.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions").get().asFile)
    }

    assemble {
        dependsOn(zipChrome, zipFirefox, zipEdge)
    }
}
