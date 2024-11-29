plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.11.0")
            }
        }
    }
}
