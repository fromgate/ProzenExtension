plugins {
    kotlin("multiplatform") version "2.0.20"
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
            kotlin.srcDir("src/jsMain/kotlin")
        }
    }
}

tasks {
    val renamePageJs by creating {
        group = "build"
        description = "Rename the compiled JS to page.js"
        dependsOn("compileProductionExecutableKotlinJs")

        doLast {
            val outputDir = file("$buildDir/distributions")
            val outputFile = outputDir.resolve("page.js")
            val compiledFile = outputDir.resolve("ProzenExtension.js")

            if (compiledFile.exists()) {
                compiledFile.renameTo(outputFile)
            } else {
                throw GradleException("Compiled JS file not found!")
            }
        }
    }
}
