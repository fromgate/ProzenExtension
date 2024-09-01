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
                    outputDirectory = File("$rootDir/build/distributions/jsKt")
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            kotlin.srcDir("src/jsMain/kotlin")
            dependencies {
                implementation(project(":chrome"))
                // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.9.0-RC.2")
            }
        }
    }
}