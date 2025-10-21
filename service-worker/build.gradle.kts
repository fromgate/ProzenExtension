plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "serviceWorkerKt.js"
                cssSupport {
                    enabled = true
                }
                distribution {
                    outputDirectory = project.layout.buildDirectory.dir("distributions")
                }
                sourceMaps = (rootProject.ext["generateSourceMaps"] as Boolean)
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            kotlin.srcDir("src/jsMain/kotlin")
            dependencies {
                implementation(project(":chrome"))
            }
        }
    }
}