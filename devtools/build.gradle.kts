import zenapi.GenerateApiReferenceTask

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val prozenDev = rootProject.ext["prozenDev"] as Boolean

kotlin {
    js {
        binaries.library()
        browser {}
    }

    sourceSets {
        val jsMain by getting {
            // Реализация и заглушки взаимоисключающие: в бандл попадает только одна.
            kotlin.srcDir(if (prozenDev) "src/dev/kotlin" else "src/release/kotlin")
            dependencies {
                implementation(project(":chrome"))
                implementation(project(":common"))
                implementation(Libs.kotlinxSerializationJson)
                implementation(Libs.kotlinxCoroutinesJs)
                implementation(Libs.kotlinxHtmlJs)
            }
        }
    }
}

if (prozenDev) {
    // Эталон компилируется только в dev-сборке: в release ни задачи, ни эталона нет.
    val generateApiReference = tasks.register<GenerateApiReferenceTask>("generateApiReference") {
        group = "build"
        description = "Компилирует reference/zen-api.toml в Kotlin-константу для dev-сборки"
        referenceFile.set(rootProject.layout.projectDirectory.file("reference/zen-api.toml"))
        packageName.set("devtools")
        outputFile.set(layout.buildDirectory.file("generated/reference/ZenApiReference.kt"))
    }

    kotlin.sourceSets.named("jsMain") {
        kotlin.srcDir(layout.buildDirectory.dir("generated/reference"))
    }
    // Каталог статический, поэтому зависимость от генератора задаётся явно.
    tasks.named("compileKotlinJs") {
        dependsOn(generateApiReference)
    }
}
