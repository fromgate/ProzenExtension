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
                val version = "2.8.0" // Версия, которую можно обновлять динамически

                browserTypes.forEach { browser ->
                    val manifestFile = file("common/src/jsMain/resources/$browser/manifest.json")
                    if (manifestFile.exists()) {
                        val updatedContent = manifestFile.readText().replace(
                            Regex("\"version\": \"\\d+\\.\\d+\\.\\d+\""),
                            "\"version\": \"$version\""
                        )
                        manifestFile.writeText(updatedContent)
                        println("Updated manifest for $browser.")
                    }
                }
            }
        }

        val processResourcesForChrome by creating(Copy::class) {
            group = "build"
            description = "Process resources for Chrome"
            from("common/src/jsMain/resources/chrome")
            from(layout.buildDirectory.dir("processedResources/js"))
            into(layout.buildDirectory.dir("dist/chrome"))
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val processResourcesForFirefox by creating(Copy::class) {
            group = "build"
            description = "Process resources for Firefox"
            from("common/src/jsMain/resources/firefox")
            from(layout.buildDirectory.dir("processedResources/js"))
            into(layout.buildDirectory.dir("dist/firefox"))
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val processResourcesForEdge by creating(Copy::class) {
            group = "build"
            description = "Process resources for Edge"
            from("common/src/jsMain/resources/edge")
            from(layout.buildDirectory.dir("processedResources/js"))
            into(layout.buildDirectory.dir("dist/edge"))
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(updateManifests)
        }

        val zipChrome by creating(Zip::class) {
            group = "build"
            dependsOn(processResourcesForChrome)
            from(layout.buildDirectory.dir("dist/chrome")) {
                into("prozen") // Вкладываем файлы в папку "prozen" внутри архива
            }
            archiveFileName.set("prozen-chrome.zip")
            destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))
            doLast {
                println("Created archive: ${archiveFileName.get()} at ${destinationDirectory.get().asFile.absolutePath}")
            }
        }

        val zipFirefox by creating(Zip::class) {
            group = "build"
            dependsOn(processResourcesForFirefox)
            from(layout.buildDirectory.dir("dist/firefox")) // Все файлы будут на корневом уровне архива
            archiveFileName.set("prozen-firefox.zip")
            destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))
            doLast {
                println("Created archive: ${archiveFileName.get()} at ${destinationDirectory.get().asFile.absolutePath}")
            }
        }

        val zipEdge by creating(Zip::class) {
            group = "build"
            dependsOn(processResourcesForEdge)
            from(layout.buildDirectory.dir("dist/edge")) {
                into("prozen") // Вкладываем файлы в папку "prozen" внутри архива
            }
            archiveFileName.set("prozen-edge.zip")
            destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))
            doLast {
                println("Created archive: ${archiveFileName.get()} at ${destinationDirectory.get().asFile.absolutePath}")
            }
        }

        val buildAll by creating {
            group = "build"
            description = "Builds all browser extensions"
            dependsOn(zipChrome, zipFirefox, zipEdge)
        }
    }
}
