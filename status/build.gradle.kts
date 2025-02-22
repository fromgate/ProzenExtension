plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "statusKt.js"
                cssSupport {
                    enabled = true
                }
                distribution {
                    outputDirectory = File("$rootDir/build/distributions/jsKt")
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
                implementation(kotlin("stdlib-js"))
                implementation(project(":chrome"))
                implementation(project(":common"))
                implementation(project(":requester"))
                implementation(project(":page-analyzer"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.11.0")
            }
        }
    }
}