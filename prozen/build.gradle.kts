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

tasks.named("jsProcessResources", Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks {
    val extensionFolder = "$rootDir/build/extension"
    val extensionZipFolder = "$rootDir/build/extension-zip"

    val copyBundleFile = register<Copy>("copyBundleFile") {
        dependsOn(":page:jsBrowserDistribution", ":prozen:jsBrowserDistribution") // Зависимость от jsBrowserDistribution задачи модуля page
        from("$rootDir/build/distributions/page.js")
        from("$rootDir/build/distributions/page.js.map")
        into("$extensionFolder/js")
    }

    val copyResources = register<Copy>("copyResources") {
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
