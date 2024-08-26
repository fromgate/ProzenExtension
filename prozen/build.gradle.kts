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
    //val page = ":page:browserDistribution"
    val page = project(":page").tasks.named("browserDistribution")

    val extensionFolder = "$projectDir/build/extension"

    val copyBundleFile = register<Copy>("copyBundleFile") {
        dependsOn(page)
        from("$projectDir/../build/distributions/page.js")
        into(extensionFolder)
    }

    val copyResources = register<Copy>("copyResources") {
        val resourceFolder = "src/jsMain/resources"
        from(
            "$resourceFolder/*.*",
            "$resourceFolder/_locales",
            "$resourceFolder/css",
            "$resourceFolder/icons",
            "$resourceFolder/img",
            "$resourceFolder/js",
        )
        into(extensionFolder)
    }

    val buildExtension = register("buildExtension") {
        dependsOn(copyBundleFile, copyResources)
    }

    val packageExtension = register<Zip>("packageExtension") {
        dependsOn(buildExtension)
        from(extensionFolder)
    }
}

/* tasks.named("jsProcessResources", Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Zip>("buildZip") {
    archiveFileName.set("prozen-chrome.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    from(tasks.named("jsBrowserProductionWebpack")) {
        into("prozen")
    }
    from(tasks.named("jsProcessResources")) {
        into("prozen")
    }
} */

