plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled = true
                }
                distribution {
                    outputDirectory = File("$rootDir/build/distributions/")
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            resources.srcDir("src/jsMain/resources")
            dependencies {
                implementation(project(":page"))
            }
        }
    }
}

val version = getVersionFromChangelog()

fun getVersionFromChangelog(): String {
    val changelogFile = file("$rootDir/CHANGELOG.md")
    val versionRegex = Regex("""^### (\d+\.\d+\.\d+)""")
    for (line in changelogFile.readLines()) {
        val matchResult = versionRegex.find(line)
        if (matchResult != null) {
            return matchResult.groupValues[1]
        }
    }
    throw GradleException("Version not found in CHANGELOG.md")
}

tasks.named("jsProcessResources", Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks {
    val extensionFolder = "$rootDir/build/extension"
    val extensionZipFolder = "$rootDir/build/extension-zip"

    val updateManifest = register("updateManifest") {
        group = "build"
        description = "Updates manifest.json with the version from CHANGELOG.md"
        val chromeManifestFile = file("$rootDir/prozen/src/jsMain/resources/manifest.json")
        val firefoxManifestFile = file("$rootDir/prozen-firefox/src/jsMain/resources/manifest.json")
        doLast {
            listOf(chromeManifestFile, firefoxManifestFile).forEach { manifestFile ->
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

    val copyBundleFile = register<Copy>("copyBundleFile") {
        dependsOn(
            ":page:jsBrowserDistribution",
            ":popup:jsBrowserDistribution",
            ":service-worker:jsBrowserDistribution",
            ":content:jsBrowserDistribution",
            ":prozen:jsBrowserDistribution")
        from("$rootDir/build/distributions/jsKt")
        into("$extensionFolder/js")
    }

    val copyResources = register<Copy>("copyResources") {
        dependsOn (updateManifest)
        from("src/jsMain/resources")
        into(extensionFolder)
    }

    val buildExtension = register("buildExtension") {
        dependsOn(copyBundleFile, copyResources)
    }

    val packageExtension = register<Zip>("packageExtension") {
        dependsOn(buildExtension)
        destinationDirectory.set(file(extensionZipFolder))
        from(extensionFolder)
        into("prozen") // для Chrome
    }
}
