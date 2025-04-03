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
                implementation(project(":common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("io.ktor:ktor-client-core:3.1.2")
                implementation("io.ktor:ktor-client-js:3.1.2")
                implementation("io.ktor:ktor-client-serialization:3.1.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
                // implementation("io.ktor:ktor-client-json:3.0.3")
            }
        }
    }
}
