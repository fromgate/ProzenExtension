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
    tasks {
        val updateManifests by creating {
            group = "build"
            description = "Updates manifest.json for each browser"

            doLast {
                val browserTypes = listOf("chrome", "firefox", "edge")
                val version = "2.8.0"

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
            from("common/src/jsMain/resources/chrome")
            from("common/build/processedResources/js")
            into("$buildDir/dist/chrome")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val processResourcesForFirefox by creating(Copy::class) {
            group = "build"
            description = "Process resources for Firefox"
            from("common/src/jsMain/resources/firefox")
            from("common/build/processedResources/js")
            into("$buildDir/dist/firefox")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val processResourcesForEdge by creating(Copy::class) {
            group = "build"
            description = "Process resources for Edge"
            from("common/src/jsMain/resources/edge")
            from("common/build/processedResources/js")
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

        val buildAll by creating {
            group = "build"
            description = "Builds all browser extensions"
            dependsOn(zipChrome, zipFirefox, zipEdge)
        }
    }
}
