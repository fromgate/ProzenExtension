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
    }

    sourceSets {
        val jsMain by getting {
            resources.srcDir("src/jsMain/resources")
        }
    }
}

tasks {
    // Устанавливаем стратегию обработки дубликатов для ресурсов
    val jsProcessResources by getting(Copy::class) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
