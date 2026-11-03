import zenapi.VerifyApiReferenceTask

plugins {
    kotlin("multiplatform") version "2.4.20" // apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
}

// ---------------------------------------------------------------------------
// Режим сборки.
//
// 1. Явный -Pprozen.dev=true|false важнее всего.
// 2. Иначе задача buildVersion означает release, а любая другая сборка — dev.
//
// Значение нужно подпроектам при конфигурации (source maps, набор модулей,
// source set devtools), поэтому оно вычисляется здесь, до их настройки.
//
// Dev и release нельзя собрать в одном Gradle-процессе (различаются source set
// и список модулей), поэтому команда, собирающая релиз, запускает dev-вариант
// отдельным вложенным процессом (см. buildRelease/buildVersion ниже).
// ---------------------------------------------------------------------------

private val prozenDevProperty = providers.gradleProperty("prozen.dev").orNull

private val prozenDevRequested: Boolean? = when (prozenDevProperty) {
    null -> null
    "true" -> true
    "false" -> false
    else -> throw GradleException(
        "Неверное значение -Pprozen.dev=\"$prozenDevProperty\": допустимо true или false"
    )
}

private val releaseInvocation = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .firstOrNull { it == "buildVersion" || it == "buildRelease" }

if (releaseInvocation != null && prozenDevRequested == true) {
    throw GradleException(
        "$releaseInvocation собирает release-версию и несовместим с -Pprozen.dev=true. " +
            "Для dev-сборки используйте ./gradlew build."
    )
}

private val prozenDev: Boolean = prozenDevRequested ?: (releaseInvocation == null)

ext["prozenDev"] = prozenDev
// Раньше это выставлялось в конфигурации buildVersion, то есть уже после того,
// как подпроекты прочитали значение. Теперь source maps зависят от флага.
ext["generateSourceMaps"] = prozenDev

kotlin {
    js(IR) {
        browser {
        }
        binaries.executable()
    }
}

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

tasks.register<VerifyApiReferenceTask>("verifyApiReference") {
    group = "verification"
    description = "Проверяет reference/zen-api.toml без записи артефактов"
    referenceFile.set(layout.projectDirectory.file("reference/zen-api.toml"))
}

/**
 * Защита release: в собранном расширении не должно быть ни devtools-кода,
 * ни эталона, ни dev-разрешений. Запускается только в release-сборке.
 * Также проверяет архив исходников для Firefox AMO: в нём не должно быть
 * dev-кода и эталона API, иначе из исходников можно было бы собрать dev-вариант.
 */
tasks.register<CheckReleaseBundleTask>("checkReleaseBundle") {
    group = "verification"
    description = "Проверяет, что в release-бандле нет dev-кода и dev-разрешений"
    dev.set(prozenDev)
    extensionDir.set(layout.buildDirectory.dir("extension"))
    forbiddenFiles.set(listOf("js/devtoolsKt.js"))
    forbiddenMarkers.set(
        listOf(
            "ZEN_API_REFERENCE",
            "volatileParams",
            "new-endpoint",
            "new-variant",
            "unverified",
            "непустой array",
            "diff:",
            "Prozen dev",
        )
    )
    forbiddenPermissions.set(listOf("unlimitedStorage"))
    sourcesZip.set(layout.buildDirectory.file("extension-zip/prozen-src.zip"))
    forbiddenZipEntries.set(listOf("prozen-src/reference", "prozen-src/devtools/src/dev"))
    if (!prozenDev) {
        dependsOn(":prozen:packageReleaseExtensions", ":prozen:packageSourcesZip")
    }
}

/** Вложенный запуск gradlew: dev и release собираются разными процессами. */
fun runGradle(vararg args: String) {
    val command = mutableListOf(File(rootDir, "gradlew").absolutePath)
    if (gradle.startParameter.isOffline) {
        command += "--offline"
    }
    command += args
    runCommandChecked(*command.toTypedArray())
}

tasks.named("build") {
    if (prozenDev) {
        dependsOn(":prozen:packageDevExtensions", ":prozen:cleanReleaseArtifacts")
    } else {
        // Релиз подразумевает и dev-варианты: dev собирается первым (и заодно
        // чистит протухший release), затем release пересоздаётся и проверяется.
        dependsOn("buildRelease")
    }
}

tasks.register("buildRelease") {
    group = "build"
    description = "Собирает dev-варианты вложенным запуском, затем release (без тега)"
    doFirst {
        if (prozenDev) {
            throw GradleException(
                "buildRelease собирает release-версию. " +
                    "Запустите без -Pprozen.dev=true (для dev-сборки используйте ./gradlew build)."
            )
        }
    }
    doLast {
        // Dev-варианты собираются только в полном дереве исходников: для dev-сборки
        // нужен эталон API. Урезанный архив исходников (для Firefox AMO) его не
        // содержит — там собирается только release.
        if (File(rootDir, "reference/zen-api.toml").isFile) {
            runGradle("-Pprozen.dev=true", "build")
        } else {
            println("reference/zen-api.toml не найден: dev-варианты пропускаются, собирается только release")
        }
        runGradle("-Pprozen.dev=false", ":prozen:packageReleaseExtensions", "checkReleaseBundle")
    }
}

tasks.register("buildVersion") {
    group = "build"
    description = "Build the release and dev variants and tag the current version"
    // Тонкий оркестратор: оба варианта собираются вложенными запусками
    // (см. комментарий про режим сборки выше), затем ставится тег.
    doLast {
        runGradle("-Pprozen.dev=true", "build")
        runGradle("-Pprozen.dev=false", ":prozen:tagVersion")
    }
}
