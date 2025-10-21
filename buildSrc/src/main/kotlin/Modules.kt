import org.gradle.api.Project

val DISALLOWED_MODULES = setOf(
    "common",
    "requester",
    "page-analyzer",
    "chrome",
    "prozen-firefox"
)

fun Project.getJsSourceModules(): List<String> =
    rootProject.subprojects
        .map { it.name }
        .filterNot { it in DISALLOWED_MODULES }