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
        }
    }
}

tasks.named("jsProcessResources", Copy::class) {
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
}

