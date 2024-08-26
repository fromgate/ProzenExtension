plugins {
    kotlin("multiplatform")
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

tasks.register<Copy>("renamePageJs") {
    group = "build"
    description = "Rename the compiled JS to page.js"
    dependsOn("compileProductionExecutableKotlinJs")

    val outputDir = layout.buildDirectory.get().dir("distributions").asFile
    val outputFile = outputDir.resolve("page.js")
    val compiledFile = outputDir.resolve("ProzenExtension.js")

    doLast {
        if (compiledFile.exists()) {
            compiledFile.renameTo(outputFile)
        } else {
            throw GradleException("Compiled JS file not found!")
        }
    }
}
