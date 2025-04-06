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
                implementation(Libs.kotlinxCoroutinesJs)
                implementation(Libs.kotlinxSerializationJson)
                implementation(Libs.kotlinxDatetime)
                implementation(Libs.ktorClientCore)
                implementation(Libs.ktorClientJs)
                implementation(Libs.ktorClientSerialization)
                implementation(Libs.ktorClientSerializationKotlinxJson)
                implementation(Libs.ktorClientContentNegotiation)
            }
        }
    }
}
