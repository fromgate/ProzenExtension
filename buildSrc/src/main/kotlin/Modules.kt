import org.gradle.api.Project

val DISALLOWED_MODULES = setOf(
    "common",
    "requester",
    "page-analyzer",
    "chrome",
    // Библиотечный модуль без своего бандла: код попадает в бандлы потребителей.
    "devtools"
)

/** Модули, которые существуют только в dev-сборке. */
val DEV_ONLY_MODULES = setOf(
    "devtools"
)

fun Project.getJsSourceModules(dev: Boolean): List<String> =
    rootProject.subprojects
        .map { it.name }
        .filterNot { it in DISALLOWED_MODULES }
        .filterNot { !dev && it in DEV_ONLY_MODULES }
