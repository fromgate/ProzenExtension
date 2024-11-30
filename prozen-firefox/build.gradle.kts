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

    val copyProzenFiles = register<Copy>("copyProzenFiles") {
        dependsOn(prozenBuild)
        from("$rootDir/build/extension") // Используем файлы из prozen
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
        from(extensionFolder)
        into("")
    }
}
