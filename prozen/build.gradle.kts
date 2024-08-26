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
    val page = ":page:jsBrowserDistribution"

    val extensionFolder = "$rootDir/build/extension"
    val extensionZipFolder = "$rootDir/build/extension-zip"

    val copyBundleFile = register<Copy>("copyBundleFile") {
        mustRunAfter(":prozen:jsBrowserDistribution")
        dependsOn(page)
        from("$rootDir/build/distributions/page.js")
        from("$rootDir/build/distributions/page.js.map")
        into("$extensionFolder/js")
    }

    val copyResources = register<Copy>("copyResources") {
        val resourceFolder = "src/jsMain/resources"
        from(resourceFolder)
        into(extensionFolder)
            /*"$resourceFolder/manifest.json"
            "$resourceFolder/_locales",
            "$resourceFolder/css",
            "$resourceFolder/icons",
            "$resourceFolder/img",
            "$resourceFolder/js" */
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
