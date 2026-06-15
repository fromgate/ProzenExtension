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
                implementation(Libs.kotlinxDatetime)
                implementation(Libs.indexDb)
                implementation(Libs.kotlinxCoroutinesJs)
            }
        }
    }
}
