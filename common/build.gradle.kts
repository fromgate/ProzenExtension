plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        binaries.executable()
        browser {}
    }
    sourceSets {
        val jsMain by getting {
            kotlin.srcDir("src/jsMain/kotlin")
            dependencies {
                implementation(project(":chrome"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.9.0-RC.2")
            }
        }
    }
}
