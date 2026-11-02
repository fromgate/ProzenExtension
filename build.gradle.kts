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
// Значение нужно подпроектам при конфигурации (source maps, набор модулей),
// поэтому оно вычисляется здесь, до их настройки.
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

private val isBuildVersion = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') == "buildVersion"
}

if (isBuildVersion && prozenDevRequested == true) {
    throw GradleException(
        "buildVersion собирает release-версию и несовместим с -Pprozen.dev=true. " +
            "Для dev-сборки используйте ./gradlew build."
    )
}

private val prozenDev: Boolean = prozenDevRequested ?: !isBuildVersion

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
 */
tasks.register<CheckReleaseBundleTask>("checkReleaseBundle") {
    group = "verification"
    description = "Проверяет, что в release-бандле нет dev-кода и dev-разрешений"
    dev.set(prozenDev)
    extensionDir.set(layout.projectDirectory.dir("build/extension"))
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
    dependsOn(":prozen:packageExtension")
}

tasks.named("build") {
    dependsOn(":prozen-firefox:packageExtension")
    // В release обычный build тоже не должен выпускать бандл с dev-кодом.
    if (!prozenDev) {
        dependsOn("checkReleaseBundle")
    }
}

tasks.register("buildVersion") {
    group = "build"
    description = "Build the release variant and tag the current version"
    dependsOn("build", ":prozen:tagVersion")
}
