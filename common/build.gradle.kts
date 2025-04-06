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
                implementation(Libs.kotlinxDatetime)
                implementation(Libs.kotlinxSerializationJson)
                implementation(Libs.kotlinxCoroutinesJs)
                implementation(Libs.kotlinxHtmlJs)
            }
        }
    }
}
