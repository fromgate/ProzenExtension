import org.jetbrains.kotlin.serialization.js.ModuleKind

plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        compilerOptions {
            sourceMap.set(true)
        }
        browser {
            commonWebpackConfig {
                outputFileName = "types-chrome.js"
                cssSupport {
                    enabled = true
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            kotlin.srcDir("src/jsMain/kotlin")
        }
    }
}