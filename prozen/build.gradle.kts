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
    val page = project(":page").tasks.named("jsBrowserDistribution")

    val extensionFolder = "$rootDir/build/extension"

    val copyBundleFile = register<Copy>("copyBundleFile") {
        dependsOn(page)
        from("$rootDir/build/distributions/page.js")
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
        from(extensionFolder)
    }
}
