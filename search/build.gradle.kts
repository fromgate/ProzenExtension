plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "searchKt.js"
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
                implementation(kotlin("stdlib-js"))
                implementation(project(":chrome"))
                implementation(project(":common"))
                implementation(project(":requester"))
                implementation(Libs.kotlinxDatetime)
                implementation(Libs.kotlinxSerializationJson)
                implementation(Libs.kotlinxCoroutinesJs)
                implementation(Libs.kotlinxHtmlJs)
            }
        }
    }
}