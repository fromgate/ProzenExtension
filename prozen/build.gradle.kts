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
val prozenDev = rootProject.ext["prozenDev"] as Boolean

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
    val extensionFolder = if (prozenDev) "$rootDir/build/extension-dev" else "$rootDir/build/extension"
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
        // Проверка release-бандла должна пройти до создания и отправки тега.
        dependsOn(rootProject.tasks.named("checkReleaseBundle"))
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

    val modules = rootProject.getJsSourceModules(prozenDev)

    // Каталог расширения — выход сборки: чистим целиком, иначе в release могут
    // пережить dev-артефакты (например, старые source maps).
    val cleanExtension = register<Delete>("cleanExtension") {
        delete(extensionFolder)
    }

    val copyBundleFile = register<Copy>("copyBundleFile") {
        into("$rootDir/build/distributions/")
        dependsOn(cleanExtension)
        dependsOn( modules.map { ":$it:jsBrowserDistribution" })
        modules.forEach {
            from (project(":$it").layout.buildDirectory.dir("distributions")) {}
        }
        into("$extensionFolder/js")
        exclude {
            it.name.matches(Regex("""\d+\.js(\.map)?"""))
                    || it.name.endsWith(".LICENSE.txt")
        }
        // В release source maps не создаются, но в каталогах модулей могли остаться
        // от предыдущих сборок.
        if (!prozenDev) {
            exclude("**/*.map")
        }

    }

    val copyResources = register<Copy>("copyResources") {
        dependsOn(cleanExtension, updateManifest)
        from("src/jsMain/resources")
        into(extensionFolder)
        if (prozenDev) {
            // Исходный манифест остаётся release-версией; dev-правки — только в копии.
            doLast {
                writeDevManifest(
                    source = file("src/jsMain/resources/manifest.json"),
                    target = file("$extensionFolder/manifest.json"),
                )
            }
        }
    }

    val buildExtension = register("buildExtension") {
        dependsOn(copyBundleFile, copyResources)
    }

    val packageExtension = register<Zip>("packageExtension") {
        dependsOn(buildExtension)
        destinationDirectory.set(file(extensionZipFolder))
        archiveFileName.set(if (prozenDev) "prozen-dev.zip" else "prozen.zip")
        from(extensionFolder)
        into("prozen") // для Chrome
    }
}