plugins {
    kotlin("multiplatform")
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

val extensionVersion = getVersionFromChangelog()

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
                    "\"version\": \"$extensionVersion\""
                )
                manifestFile.writeText(updatedContent)
            }
            println("Manifests updated to version $extensionVersion")
        }
    }

    register("tagVersion") {
        group = "versioning"
        description = "Tags the last commit with the version number"
        doLast {
            val existingTags = runCommand ("git", "tag")
            if (existingTags.contains(extensionVersion)) {
                println("Tag $extensionVersion already exists, skipping.")
            } else {
                println("Tagging commit with version $version")
                runCommand("git", "tag", "v$extensionVersion")
                runCommand("git", "push", "origin", "v$extensionVersion")
            }
        }
    }

    val modules = rootProject.getJsSourceModules()

    val copyBundleFile = register<Copy>("copyBundleFile") {
        into("$rootDir/build/distributions/")
        dependsOn( modules.map { ":$it:jsBrowserDistribution" })
        modules.forEach {
            from (project(":$it").layout.buildDirectory.dir("distributions")) {}
        }
        into("$extensionFolder/js")
        exclude("731.js")
        exclude("731.js.map")
        exclude("*js.LICENSE.txt")
    }

    val copyResources = register<Copy>("copyResources") {
        dependsOn(updateManifest)
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