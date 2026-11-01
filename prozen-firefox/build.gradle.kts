plugins {
    kotlin("multiplatform")
}

val prozenDev = rootProject.ext["prozenDev"] as Boolean

kotlin {
    js {
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
                implementation(project(":prozen"))
            }
        }
    }
}

tasks.named("jsProcessResources", Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("jsBrowserDistribution") {
    dependsOn(":prozen:copyBundleFile")
}

tasks {
    val prozenBuild = ":prozen:packageExtension"

    val extensionFolder = "$rootDir/build/extension-firefox"
    val extensionZipFolder = "$rootDir/build/extension-zip"
    // Firefox dev отличается от release только JS и именем архива: манифест обычный.
    val prozenExtensionFolder =
        if (prozenDev) "$rootDir/build/extension-dev" else "$rootDir/build/extension"

    val copyProzenFiles = register<Copy>("copyProzenFiles") {
        dependsOn(prozenBuild)
        // Каталог — выход этой задачи: чистим, чтобы dev-файлы не пережили release (и наоборот).
        doFirst { delete(extensionFolder) }
        from(prozenExtensionFolder) // Используем файлы из prozen
        into(extensionFolder)
    }

    val copyFirefoxResources = register<Copy>("copyFirefoxResources") {
        dependsOn(copyProzenFiles)
        from("src/jsMain/resources") // /manifest.json
        into(extensionFolder)
    }

    val buildExtension = register("buildExtension") {
        dependsOn(copyFirefoxResources)
    }

    val packageExtension = register<Zip>("packageExtension") {
        dependsOn(buildExtension)
        mustRunAfter(":prozen:packageExtension")
        destinationDirectory.set(file(extensionZipFolder))
        archiveFileName.set(if (prozenDev) "prozen-firefox-dev.zip" else "prozen-firefox.zip")
        from(extensionFolder)
        into("")
    }
}
