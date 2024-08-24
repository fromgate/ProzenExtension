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

    val copyJsFiles by creating(Copy::class) {
        from("src/jsMain/js")
        into(layout.buildDirectory.dir("processedResources/js/jsMain").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val copyHtmlAndCssFiles by creating(Copy::class) {
        from("src/jsMain/resources/common")
        into(layout.buildDirectory.dir("processedResources/resources/common").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val assemblePopupJs by creating(Sync::class) {
        dependsOn("jsBrowserProductionWebpack", copyJsFiles)
        from(layout.buildDirectory.dir("compileSync/js/jsMain/productionExecutable/kotlin/popup").get().asFile)
        from(layout.buildDirectory.dir("processedResources/js/jsMain/popup.js").get().asFile)
        into(layout.buildDirectory.dir("dist/popup").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val assembleContentJs by creating(Sync::class) {
        dependsOn("jsBrowserProductionWebpack", copyJsFiles)
        from(layout.buildDirectory.dir("compileSync/js/jsMain/productionExecutable/kotlin/content").get().asFile)
        from(layout.buildDirectory.dir("processedResources/js/jsMain/content-script.js").get().asFile)
        into(layout.buildDirectory.dir("dist/content").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val assembleOptionsJs by creating(Sync::class) {
        dependsOn("jsBrowserProductionWebpack", copyJsFiles)
        from(layout.buildDirectory.dir("compileSync/js/jsMain/productionExecutable/kotlin/options").get().asFile)
        from(layout.buildDirectory.dir("processedResources/js/jsMain/options.js").get().asFile)
        into(layout.buildDirectory.dir("dist/options").get().asFile)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val assembleChrome by creating(Copy::class) {
        dependsOn(updateManifests, assemblePopupJs, assembleContentJs, assembleOptionsJs, copyHtmlAndCssFiles)
        group = "build"
        description = "Assemble Chrome extension"
        from("src/jsMain/resources/chrome") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        from(layout.buildDirectory.dir("processedResources/resources/common").get().asFile)
        from(layout.buildDirectory.dir("dist/popup").get().asFile) { into("prozen/popup") }
        from(layout.buildDirectory.dir("dist/content").get().asFile) { into("prozen/content") }
        from(layout.buildDirectory.dir("dist/options").get().asFile) { into("prozen/options") }
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

    val assembleFirefox by creating(Copy::class) {
        dependsOn(updateManifests, assemblePopupJs, assembleContentJs, assembleOptionsJs, copyHtmlAndCssFiles)
        group = "build"
        description = "Assemble Firefox extension"
        from("src/jsMain/resources/firefox") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        from(layout.buildDirectory.dir("processedResources/resources/common").get().asFile)
        from(layout.buildDirectory.dir("dist/popup").get().asFile) { into("prozen/popup") }
        from(layout.buildDirectory.dir("dist/content").get().asFile) { into("prozen/content") }
        from(layout.buildDirectory.dir("dist/options").get().asFile) { into("prozen/options") }
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

    val assembleEdge by creating(Copy::class) {
        dependsOn(updateManifests, assemblePopupJs, assembleContentJs, assembleOptionsJs, copyHtmlAndCssFiles)
        group = "build"
        description = "Assemble Edge extension"
        from("src/jsMain/resources/edge") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        from(layout.buildDirectory.dir("processedResources/resources/common").get().asFile)
        from(layout.buildDirectory.dir("dist/popup").get().asFile) { into("prozen/popup") }
        from(layout.buildDirectory.dir("dist/content").get().asFile) { into("prozen/content") }
        from(layout.buildDirectory.dir("dist/options").get().asFile) { into("prozen/options") }
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
