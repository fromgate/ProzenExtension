plugins {
    id("base")
}

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")

    tasks {
        val updateManifests by creating {
            group = "build"
            description = "Updates manifest.json for each browser"

            doLast {
                val browserTypes = listOf("chrome", "firefox", "edge")
                val version = "2.8.0" // Версию можно извлекать динамически, если нужно

                browserTypes.forEach { browser ->
                    val manifestFile = file("common/src/jsMain/resources/$browser/manifest.json")
                    if (manifestFile.exists()) {
                        val updatedContent = manifestFile.readText().replace(
                            Regex("\"version\": \"\\d+\\.\\d+\\.\\d+\""),
                            "\"version\": \"$version\""
                        )
                        manifestFile.writeText(updatedContent)
                    }
                }
            }
        }

        val processResourcesForChrome by creating(Copy::class) {
            group = "build"
            description = "Process resources for Chrome"
            from("common/src/jsMain/resources/chrome") // Берём Chrome manifest.json
            from("common/build/processedResources/js") // Остальные ресурсы
            into("$buildDir/dist/chrome")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val processResourcesForFirefox by creating(Copy::class) {
            group = "build"
            description = "Process resources for Firefox"
            from("common/src/jsMain/resources/firefox") // Берём Firefox manifest.json
            from("common/build/processedResources/js") // Остальные ресурсы
            into("$buildDir/dist/firefox")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val processResourcesForEdge by creating(Copy::class) {
            group = "build"
            description = "Process resources for Edge"
            from("common/src/jsMain/resources/edge") // Берём Edge manifest.json
            from("common/build/processedResources/js") // Остальные ресурсы
            into("$buildDir/dist/edge")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val zipChrome by creating(Zip::class) {
            group = "build"
            dependsOn(processResourcesForChrome)
            from("$buildDir/dist/chrome")
            archiveFileName.set("prozen-chrome.zip")
            destinationDirectory.set(file("$buildDir/distributions"))
        }

        val zipFirefox by creating(Zip::class) {
            group = "build"
            dependsOn(processResourcesForFirefox)
            from("$buildDir/dist/firefox")
            archiveFileName.set("prozen-firefox.zip")
            destinationDirectory.set(file("$buildDir/distributions"))
        }

        val zipEdge by creating(Zip::class) {
            group = "build"
            dependsOn(processResourcesForEdge)
            from("$buildDir/dist/edge")
            archiveFileName.set("prozen-edge.zip")
            destinationDirectory.set(file("$buildDir/distributions"))
        }

        assemble {
            dependsOn(zipChrome, zipFirefox, zipEdge)
        }
    }
}
